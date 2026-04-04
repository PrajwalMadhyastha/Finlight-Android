#!/usr/bin/env python3
"""
convert_to_tflite.py — Convert PyTorch NER Model to TFLite
===========================================================
Converts the fine-tuned MobileBERT NER model from PyTorch to TFLite format
for deployment on Android devices.

Pipeline: PyTorch → HuggingFace TF Model → TFLite
         (uses from_pretrained(from_pt=True) for weight transfer)

Note: The resulting TFLite model requires SELECT_TF_OPS (Flex ops) on Android.
      This is handled by the LiteRT dependency in the Android app.

Usage:
    python convert_to_tflite.py --model output/best_model/ --output output/
"""

import argparse
import json
import os
import sys
from pathlib import Path

# Prevent Abseil mutex deadlock on Apple Silicon (M1/M2/M3).
# USE_TF=0 prevents TF auto-import in the main process; we import TF
# explicitly in subprocesses to avoid the PyTorch/TF coexistence deadlock.
os.environ["KMP_DUPLICATE_LIB_OK"] = "True"
os.environ["USE_TF"] = "0"
os.environ["TOKENIZERS_PARALLELISM"] = "false"

import numpy as np
import torch
from transformers import AutoModelForTokenClassification, AutoTokenizer

# ============================================================================
# Configuration
# ============================================================================
MAX_SEQ_LENGTH = 128
TFLITE_FILENAME = "sms_ner.tflite"

# PyTorch baseline F1 from training_summary.json (used for regression check)
BASELINE_F1 = {
    "MERCHANT": 0.9879,
    "AMOUNT":   0.9995,
    "ACCOUNT":  0.9958,
    "BALANCE":  0.9958,
    "overall":  0.9947,
}
F1_REGRESSION_THRESHOLD = 0.005  # Alert if any entity drops >0.5%


# ============================================================================
# Step 1: Convert PyTorch → TFLite via native TF path
# ============================================================================
def convert_to_tflite(model_path: str, output_path: Path):
    """Convert the PyTorch model to TFLite via HuggingFace's native TF path.

    Runs TensorFlow in a subprocess to avoid Abseil mutex deadlock
    when PyTorch and TF coexist on macOS ARM64 + Python 3.9.
    """
    print("\n📦 Step 1: Converting PyTorch → TFLite (via native TF path)...")

    tflite_path = output_path / TFLITE_FILENAME

    import subprocess
    import textwrap

    convert_script = textwrap.dedent(f"""\
        import os, sys, json
        os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'
        os.environ['TOKENIZERS_PARALLELISM'] = 'false'

        import numpy as np
        import tensorflow as tf
        from transformers import TFAutoModelForTokenClassification, AutoTokenizer

        model_path = "{model_path}"

        print("   Loading TF model from PyTorch weights...", file=sys.stderr)
        tf_model = TFAutoModelForTokenClassification.from_pretrained(
            model_path, from_pt=True
        )
        tokenizer = AutoTokenizer.from_pretrained(model_path)

        # Build the model with a dummy forward pass
        dummy = tokenizer(
            "test", max_length={MAX_SEQ_LENGTH}, padding="max_length",
            truncation=True, return_tensors="tf"
        )
        _ = tf_model(dummy)

        # Create serving function with explicit input signatures
        @tf.function(input_signature=[
            tf.TensorSpec(shape=[1, {MAX_SEQ_LENGTH}], dtype=tf.int32, name="input_ids"),
            tf.TensorSpec(shape=[1, {MAX_SEQ_LENGTH}], dtype=tf.int32, name="attention_mask"),
        ])
        def serving_fn(input_ids, attention_mask):
            return tf_model(
                input_ids=input_ids,
                attention_mask=attention_mask,
                training=False
            )

        print("   Converting to TFLite with INT8 dynamic-range quantization...", file=sys.stderr)
        converter = tf.lite.TFLiteConverter.from_concrete_functions(
            [serving_fn.get_concrete_function()], tf_model
        )
        # TFLITE_BUILTINS only — flatbuffer inspection confirmed MobileBERT produces
        # zero Flex ops, so SELECT_TF_OPS is not needed. Removing it allows the
        # litert-select-tf-ops Gradle dependency (~80-100 MB) to be dropped.
        converter.target_spec.supported_ops = [
            tf.lite.OpsSet.TFLITE_BUILTINS,
        ]
        # INT8 dynamic-range quantization: weights → int8, activations stay float32.
        # No calibration dataset needed. Achieves ~4x model size reduction.
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        tflite_model = converter.convert()

        with open("{tflite_path}", "wb") as f:
            f.write(tflite_model)

        size = os.path.getsize("{tflite_path}")

        # Log TFLite input/output details
        interp = tf.lite.Interpreter(model_path="{tflite_path}")
        interp.allocate_tensors()
        print("   📋 TFLite input details:", file=sys.stderr)
        for d in interp.get_input_details():
            print(f"      index={{d['index']}}, name={{d['name']}}, "
                  f"shape={{d['shape']}}, dtype={{d['dtype']}}", file=sys.stderr)
        print("   📋 TFLite output details:", file=sys.stderr)
        for d in interp.get_output_details():
            print(f"      index={{d['index']}}, name={{d['name']}}, "
                  f"shape={{d['shape']}}, dtype={{d['dtype']}}", file=sys.stderr)

        print(json.dumps({{"success": True, "size": size}}))
    """)

    # Run in subprocess with USE_TF removed so HuggingFace can find TF
    env = os.environ.copy()
    env.pop("USE_TF", None)

    result = subprocess.run(
        [sys.executable, "-c", convert_script],
        capture_output=True,
        text=True,
        env=env,
    )

    # Print stderr (progress messages) to console
    if result.stderr:
        for line in result.stderr.strip().split("\n"):
            if not any(skip in line for skip in [
                "WARNING", "NotOpenSSL", "UserWarning", "urllib3",
                "absl", "INFO: Created TensorFlow"
            ]):
                print(line)

    if result.returncode != 0:
        print(f"\n❌ TFLite conversion failed!")
        if result.stdout.strip():
            try:
                err = json.loads(result.stdout.strip())
                print(f"   Error: {err.get('error', 'Unknown')}")
            except json.JSONDecodeError:
                print(f"   Output: {result.stdout.strip()[:500]}")
        if result.stderr:
            lines = result.stderr.strip().split("\n")
            for line in lines[-5:]:
                print(f"   {line}")
        sys.exit(1)

    try:
        output = json.loads(result.stdout.strip().split("\n")[-1])
        size_mb = output["size"] / 1024 / 1024
        print(f"   ✅ TFLite model saved to: {tflite_path}")
        print(f"   File size: {size_mb:.1f} MB")
        return tflite_path
    except (json.JSONDecodeError, KeyError):
        if tflite_path.exists():
            print(f"   ✅ TFLite model saved to: {tflite_path}")
            print(f"   File size: {tflite_path.stat().st_size / 1024 / 1024:.1f} MB")
            return tflite_path
        print(f"\n❌ TFLite conversion produced no output!")
        print(f"   stdout: {result.stdout[:500]}")
        sys.exit(1)


# ============================================================================
# Step 2: Validate TFLite Model
# ============================================================================
def validate_tflite(
    model, tokenizer, tflite_path: Path, num_samples: int = 10
):
    """Validate that TFLite model produces same outputs as PyTorch model.

    Runs TFLite inference in a subprocess to avoid PyTorch/TF deadlock.
    Compares predictions only on real (non-PAD) tokens.
    """
    print(f"\n🔍 Step 2: Validating TFLite model ({num_samples} samples)...")

    import subprocess
    import tempfile
    import textwrap

    # Sample texts
    test_texts = [
        "Your account debited Rs 500 at Amazon",
        "INR 1200 charged to card XX1234 for Swiggy",
        "Salary credited Rs 50000 to account XX5678",
        "EMI of Rs 5000 deducted from HDFC account",
        "Refund of Rs 299 processed to your card",
        "ATM withdrawal Rs 2000 from SBI account",
        "Bill payment Rs 1500 for Airtel",
        "UPI transfer Rs 1000 to PhonePe",
        "Credit card payment Rs 10000 received",
        "Insurance premium Rs 3000 debited",
    ][:num_samples]

    model.eval()

    # Prepare inputs and get PyTorch predictions
    all_input_ids = []
    all_attention_masks = []
    all_pt_logits = []
    all_real_lengths = []

    for text in test_texts:
        inputs = tokenizer(
            text,
            max_length=MAX_SEQ_LENGTH,
            padding="max_length",
            truncation=True,
            return_tensors="pt",
        )
        all_input_ids.append(inputs["input_ids"].numpy().astype(np.int32))
        all_attention_masks.append(inputs["attention_mask"].numpy().astype(np.int32))
        all_real_lengths.append(int(inputs["attention_mask"].sum().item()))

        with torch.no_grad():
            pt_output = model(
                input_ids=inputs["input_ids"],
                attention_mask=inputs["attention_mask"],
            )
            all_pt_logits.append(pt_output.logits.numpy()[0])

    # Save inputs to temp file for subprocess
    with tempfile.NamedTemporaryFile(suffix=".npz", delete=False) as tmp:
        tmp_path = tmp.name
        np.savez(
            tmp_path,
            input_ids=np.array(all_input_ids),
            attention_masks=np.array(all_attention_masks),
        )

    # Run TFLite inference in subprocess
    tflite_script = textwrap.dedent(f"""\
        import os, sys, json
        os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'
        import numpy as np
        import tensorflow as tf

        data = np.load("{tmp_path}")
        input_ids_all = data["input_ids"]
        attention_masks_all = data["attention_masks"]

        interpreter = tf.lite.Interpreter(model_path="{tflite_path}")
        interpreter.allocate_tensors()
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()

        results = []
        for i in range(len(input_ids_all)):
            input_ids = input_ids_all[i]
            attention_mask = attention_masks_all[i]

            # Match inputs by name (native TF path preserves names)
            for detail in input_details:
                if "input_ids" in detail["name"]:
                    interpreter.set_tensor(
                        detail["index"],
                        input_ids.astype(detail["dtype"])
                    )
                elif "attention_mask" in detail["name"]:
                    interpreter.set_tensor(
                        detail["index"],
                        attention_mask.astype(detail["dtype"])
                    )

            interpreter.invoke()
            logits = interpreter.get_tensor(output_details[0]["index"])[0]
            results.append(logits.tolist())

        # Save results
        np.save("{tmp_path}.tflite_logits.npy", np.array(results))
        print("OK")
    """)

    result = subprocess.run(
        [sys.executable, "-c", tflite_script],
        capture_output=True,
        text=True,
    )

    if result.returncode != 0:
        print(f"   ❌ TFLite validation subprocess failed!")
        if result.stderr:
            for line in result.stderr.strip().split("\n")[-5:]:
                print(f"   {line}")
        os.unlink(tmp_path)
        return

    # Load TFLite results and compare
    tflite_logits_all = np.load(f"{tmp_path}.tflite_logits.npy")
    os.unlink(tmp_path)
    os.unlink(f"{tmp_path}.tflite_logits.npy")

    id2label = model.config.id2label

    all_match = True
    for i, (pt_logits, tflite_logits, real_len) in enumerate(
        zip(all_pt_logits, tflite_logits_all, all_real_lengths)
    ):
        # Compare only real (non-PAD) tokens
        pt_real = pt_logits[:real_len]
        tfl_real = tflite_logits[:real_len]

        pt_preds = np.argmax(pt_real, axis=-1)
        tflite_preds = np.argmax(tfl_real, axis=-1)

        match = np.array_equal(pt_preds, tflite_preds)
        # Note: after INT8 quantization the raw logit magnitudes are NOT comparable
        # between PyTorch and TFLite (different scales). Only argmax matters.

        status = "✅" if match else "❌"
        print(f"   Sample {i+1}: {status}")

        if not match:
            all_match = False
            diff_indices = np.where(pt_preds != tflite_preds)[0]
            tokens = tokenizer.convert_ids_to_tokens(
                tokenizer(test_texts[i], max_length=MAX_SEQ_LENGTH,
                          padding="max_length", truncation=True)["input_ids"]
            )
            for pos in diff_indices[:5]:
                tok = tokens[pos]
                pt_label  = id2label[int(pt_preds[pos])]
                tfl_label = id2label[int(tflite_preds[pos])]
                print(f"      pos {pos} ({tok}): PT={pt_label}, TFL={tfl_label}")

    if all_match:
        print(f"\n   ✅ All {num_samples} samples match between PyTorch and TFLite!")
    else:
        print(f"\n   ❌ Some samples have prediction mismatches!")


# ============================================================================
# Step 3: Full F1 Evaluation on Test Set
# ============================================================================
def evaluate_tflite_f1(
    tflite_path: Path,
    dataset_path: str,
    label_map_path: str,
    model_dir: str,
):
    """Run the full test dataset through the TFLite model and compute seqeval F1.

    Compares per-entity F1 against the PyTorch baseline from training_summary.json
    and flags any regression exceeding F1_REGRESSION_THRESHOLD.
    """
    print(f"\n📊 Step 3: Full F1 Evaluation on Test Set ({tflite_path.name})...")

    import subprocess
    import tempfile
    import textwrap

    eval_script = textwrap.dedent(f"""\
        import os, sys, json
        os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'
        os.environ['TOKENIZERS_PARALLELISM'] = 'false'

        import numpy as np
        import tensorflow as tf
        from transformers import AutoTokenizer
        from datasets import load_from_disk

        MAX_SEQ_LENGTH = {MAX_SEQ_LENGTH}
        LABEL_LIST = [
            "O",
            "B-MERCHANT", "I-MERCHANT",
            "B-AMOUNT",   "I-AMOUNT",
            "B-ACCOUNT",  "I-ACCOUNT",
            "B-BALANCE",  "I-BALANCE",
        ]
        ID_TO_LABEL = {{i: l for i, l in enumerate(LABEL_LIST)}}
        IGNORED_LABEL_ID = -100

        # Load dataset and tokenizer
        dataset = load_from_disk("{dataset_path}")
        test_set  = dataset["test"]
        with open("{label_map_path}") as f:
            lm = json.load(f)
        id2label = {{int(k): v for k, v in lm["id_to_label"].items()}}

        tokenizer = AutoTokenizer.from_pretrained("{model_dir}")

        print(f"   Test samples: {{len(test_set)}}", file=sys.stderr)

        # Load TFLite interpreter
        interp = tf.lite.Interpreter(model_path="{tflite_path}")
        interp.allocate_tensors()
        input_details  = interp.get_input_details()
        output_details = interp.get_output_details()

        # Map tensor indices by name
        def get_index(name_part):
            for d in input_details:
                if name_part in d["name"]:
                    return d["index"], d["dtype"]
            return None, None

        ids_idx,  ids_dtype  = get_index("input_ids")
        mask_idx, mask_dtype = get_index("attention_mask")

        true_sequences = []
        pred_sequences = []

        for i, sample in enumerate(test_set):
            if i % 500 == 0:
                print(f"   Evaluating sample {{i}}/{{len(test_set)}}...", file=sys.stderr)

            # Tokenize (re-use pre-tokenized ids stored in the dataset)
            input_ids_np      = np.array([sample["input_ids"]], dtype=ids_dtype)
            attention_mask_np = np.array([sample["attention_mask"]], dtype=mask_dtype)

            interp.set_tensor(ids_idx,  input_ids_np)
            interp.set_tensor(mask_idx, attention_mask_np)
            interp.invoke()

            logits = interp.get_tensor(output_details[0]["index"])[0]  # [128, 9]
            pred_ids = np.argmax(logits, axis=-1)

            labels = sample["labels"]
            true_seq = []
            pred_seq = []
            for pred_id, label_id in zip(pred_ids, labels):
                if label_id == IGNORED_LABEL_ID:
                    continue
                true_seq.append(id2label[label_id])
                pred_seq.append(id2label[pred_id])
            true_sequences.append(true_seq)
            pred_sequences.append(pred_seq)

        # Compute F1 with seqeval (same library used during training)
        from seqeval.metrics import (
            f1_score as seqeval_f1,
            precision_score as seqeval_precision,
            recall_score as seqeval_recall,
            classification_report,
        )

        overall_f1  = seqeval_f1(true_sequences, pred_sequences)
        overall_pre = seqeval_precision(true_sequences, pred_sequences)
        overall_rec = seqeval_recall(true_sequences, pred_sequences)

        entity_types = ["MERCHANT", "AMOUNT", "ACCOUNT", "BALANCE"]
        per_entity = {{}}
        for ent in entity_types:
            t = [[x if x.endswith(ent) else "O" for x in s] for s in true_sequences]
            p = [[x if x.endswith(ent) else "O" for x in s] for s in pred_sequences]
            per_entity[ent] = {{
                "precision": round(seqeval_precision(t, p), 4),
                "recall":    round(seqeval_recall(t, p),    4),
                "f1":        round(seqeval_f1(t, p),        4),
            }}

        result = {{
            "overall": {{
                "precision": round(overall_pre, 4),
                "recall":    round(overall_rec, 4),
                "f1":        round(overall_f1,  4),
            }},
            "per_entity": per_entity,
            "report": classification_report(true_sequences, pred_sequences, digits=4),
        }}
        print(json.dumps(result))
    """)

    env = os.environ.copy()
    env.pop("USE_TF", None)

    result = subprocess.run(
        [sys.executable, "-c", eval_script],
        capture_output=True,
        text=True,
        env=env,
    )

    if result.stderr:
        for line in result.stderr.strip().split("\n"):
            if not any(skip in line for skip in [
                "WARNING", "NotOpenSSL", "UserWarning", "urllib3",
                "absl", "INFO: Created TensorFlow",
            ]):
                print(line)

    if result.returncode != 0:
        print(f"   ❌ F1 evaluation subprocess failed!")
        if result.stderr:
            for line in result.stderr.strip().split("\n")[-8:]:
                print(f"   {line}")
        return

    try:
        output = json.loads(result.stdout.strip().split("\n")[-1])
    except json.JSONDecodeError:
        print(f"   ❌ Could not parse F1 evaluation output.")
        print(f"   stdout: {result.stdout[:500]}")
        return

    overall = output["overall"]
    per_entity = output["per_entity"]

    print(f"\n   {'Entity':<12} {'Precision':>10} {'Recall':>8} {'F1 (TFLite)':>12} {'F1 (Baseline)':>14} {'Δ F1':>8} {'Status':>8}")
    print(f"   {'-'*76}")

    all_ok = True
    for ent in ["MERCHANT", "AMOUNT", "ACCOUNT", "BALANCE"]:
        m     = per_entity[ent]
        base  = BASELINE_F1[ent]
        delta = m["f1"] - base
        ok    = abs(delta) <= F1_REGRESSION_THRESHOLD
        if not ok:
            all_ok = False
        flag  = "✅" if ok else "⚠️ REGRESSION"
        print(f"   {ent:<12} {m['precision']:>10.4f} {m['recall']:>8.4f} {m['f1']:>12.4f} {base:>14.4f} {delta:>+8.4f} {flag:>8}")

    print(f"   {'-'*76}")
    base_overall = BASELINE_F1["overall"]
    delta_overall = overall["f1"] - base_overall
    ok_overall = abs(delta_overall) <= F1_REGRESSION_THRESHOLD
    if not ok_overall:
        all_ok = False
    flag = "✅" if ok_overall else "⚠️ REGRESSION"
    print(f"   {'OVERALL':<12} {overall['precision']:>10.4f} {overall['recall']:>8.4f} {overall['f1']:>12.4f} {base_overall:>14.4f} {delta_overall:>+8.4f} {flag:>8}")

    print(f"\n   Full seqeval classification report:")
    for line in output["report"].split("\n"):
        print(f"   {line}")

    if all_ok:
        print(f"\n   ✅ F1 regression check PASSED — quantized model is safe to deploy.")
    else:
        print(f"\n   ⚠️  F1 regression check FAILED — review above before deploying.")


# ============================================================================
# Main
# ============================================================================
def main():
    parser = argparse.ArgumentParser(description="Convert NER model to TFLite")
    parser.add_argument("--model",    required=True, help="Path to the trained PyTorch model directory")
    parser.add_argument("--output",   required=True, help="Directory to save TFLite model")
    parser.add_argument("--dataset",  default=None,  help="Path to HuggingFace dataset dir (for F1 eval). Defaults to <model>/../dataset")
    parser.add_argument("--skip-validation", action="store_true", help="Skip TFLite validation and F1 evaluation")
    args = parser.parse_args()

    print("=" * 60)
    print("NER Model → TFLite Conversion")
    print("=" * 60)

    model_path = Path(args.model)
    output_path = Path(args.output)
    output_path.mkdir(parents=True, exist_ok=True)

    # Load model and tokenizer
    print(f"\n📥 Loading model from: {model_path}")
    tokenizer = AutoTokenizer.from_pretrained(str(model_path))
    model = AutoModelForTokenClassification.from_pretrained(str(model_path))
    print(f"   Labels: {model.config.id2label}")

    # Step 1: PyTorch → TFLite (via native TF path, INT8 quantized)
    tflite_path = convert_to_tflite(str(model_path), output_path)

    # Step 2: Quick prediction-match validation (10 samples)
    if not args.skip_validation and tflite_path:
        validate_tflite(model, tokenizer, tflite_path)

    # Step 3: Full F1 evaluation on test set vs PyTorch baseline
    if not args.skip_validation and tflite_path:
        # Resolve dataset and label map alongside the model dir
        dataset_dir  = str(model_path.parent / "dataset")
        label_map    = str(model_path / "ner_label_map.json")
        if not Path(dataset_dir).exists():
            print(f"\n⚠️  Dataset not found at {dataset_dir}, skipping F1 evaluation.")
            print(f"   Pass --dataset <path> to specify the dataset location.")
        else:
            evaluate_tflite_f1(tflite_path, dataset_dir, label_map, str(model_path))

    # Copy label map
    label_map_src = model_path / "ner_label_map.json"
    label_map_dst = output_path / "ner_label_map.json"
    if label_map_src.exists():
        import shutil
        shutil.copy2(str(label_map_src), str(label_map_dst))
        print(f"\n📋 Label map copied to: {label_map_dst}")

    print(f"\n✅ Conversion complete!")
    print(f"   TFLite model: {output_path / TFLITE_FILENAME}")
    print(f"   Label map:    {label_map_dst}")
    print(f"\n⚠️  Note: This TFLite model requires SELECT_TF_OPS (Flex ops) on Android.")
    print(f"   The LiteRT dependency in the app already supports this.")
    print(f"\nNext step: Copy {TFLITE_FILENAME} to app/src/main/assets/")


if __name__ == "__main__":
    main()
