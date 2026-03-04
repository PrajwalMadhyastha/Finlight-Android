#!/usr/bin/env python3
"""
train_ner.py — Fine-tune MobileBERT for Token Classification (NER)
====================================================================
Loads the prepared dataset from prepare_data.py and fine-tunes
google/mobilebert-uncased for SMS entity extraction.

Usage:
    python train_ner.py --data output/dataset/ --output output/best_model/
"""

import argparse
import json
import os
import sys
from pathlib import Path

import numpy as np
from datasets import load_from_disk
from transformers import (
    AutoModelForTokenClassification,
    AutoTokenizer,
    TrainingArguments,
    Trainer,
    DataCollatorForTokenClassification,
    EarlyStoppingCallback,
)
from seqeval.metrics import (
    classification_report as seqeval_report,
    f1_score as seqeval_f1,
    precision_score as seqeval_precision,
    recall_score as seqeval_recall,
)

# ============================================================================
# Configuration
# ============================================================================
MODEL_NAME = "google/mobilebert-uncased"
IGNORED_LABEL_ID = -100

# Must match prepare_data.py
LABEL_LIST = [
    "O",
    "B-MERCHANT", "I-MERCHANT",
    "B-AMOUNT", "I-AMOUNT",
    "B-ACCOUNT", "I-ACCOUNT",
    "B-BALANCE", "I-BALANCE",
]
ID_TO_LABEL = {i: label for i, label in enumerate(LABEL_LIST)}
LABEL_TO_ID = {label: i for i, label in enumerate(LABEL_LIST)}


# ============================================================================
# Metrics Computation
# ============================================================================
def compute_metrics(eval_pred):
    """Compute per-entity precision, recall, F1 using seqeval."""
    predictions, labels = eval_pred
    predictions = np.argmax(predictions, axis=2)

    # Convert IDs back to label strings, ignoring special tokens
    true_labels = []
    pred_labels = []

    for pred_seq, label_seq in zip(predictions, labels):
        true_seq = []
        pred_seq_labels = []
        for pred_id, label_id in zip(pred_seq, label_seq):
            if label_id == IGNORED_LABEL_ID:
                continue
            true_seq.append(ID_TO_LABEL[label_id])
            pred_seq_labels.append(ID_TO_LABEL[pred_id])
        true_labels.append(true_seq)
        pred_labels.append(pred_seq_labels)

    return {
        "precision": seqeval_precision(true_labels, pred_labels),
        "recall": seqeval_recall(true_labels, pred_labels),
        "f1": seqeval_f1(true_labels, pred_labels),
    }


# ============================================================================
# Main
# ============================================================================
def main():
    parser = argparse.ArgumentParser(description="Fine-tune MobileBERT for NER")
    parser.add_argument("--data", required=True, help="Path to prepared dataset (from prepare_data.py)")
    parser.add_argument("--output", required=True, help="Directory to save the best model checkpoint")
    parser.add_argument("--epochs", type=int, default=5, help="Number of training epochs (default: 5)")
    parser.add_argument("--batch-size", type=int, default=16, help="Training batch size (default: 16)")
    parser.add_argument("--lr", type=float, default=5e-5, help="Learning rate (default: 5e-5)")
    parser.add_argument("--warmup-ratio", type=float, default=0.1, help="Warmup ratio (default: 0.1)")
    args = parser.parse_args()

    print("=" * 60)
    print("NER Model Training — MobileBERT Token Classification")
    print("=" * 60)

    # Load dataset
    print(f"\n📂 Loading dataset from: {args.data}")
    dataset = load_from_disk(args.data)
    print(f"   Train: {len(dataset['train'])} examples")
    print(f"   Validation: {len(dataset['validation'])} examples")
    print(f"   Test: {len(dataset['test'])} examples")

    # Load tokenizer and model
    print(f"\n📥 Loading model: {MODEL_NAME}")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    model = AutoModelForTokenClassification.from_pretrained(
        MODEL_NAME,
        num_labels=len(LABEL_LIST),
        id2label=ID_TO_LABEL,
        label2id=LABEL_TO_ID,
    )
    print(f"   Model parameters: {model.num_parameters():,}")

    # Data collator
    data_collator = DataCollatorForTokenClassification(
        tokenizer=tokenizer,
        padding=True,
        label_pad_token_id=IGNORED_LABEL_ID,
    )

    # Training arguments
    output_dir = Path(args.output)
    training_args = TrainingArguments(
        output_dir=str(output_dir / "checkpoints"),
        eval_strategy="epoch",
        save_strategy="epoch",
        learning_rate=args.lr,
        per_device_train_batch_size=args.batch_size,
        per_device_eval_batch_size=args.batch_size * 2,
        num_train_epochs=args.epochs,
        weight_decay=0.01,
        warmup_ratio=args.warmup_ratio,
        load_best_model_at_end=True,
        metric_for_best_model="f1",
        greater_is_better=True,
        logging_steps=50,
        save_total_limit=2,
        report_to="none",  # No external logging (local only)
        fp16=False,  # Mac MPS doesn't support fp16 well, use fp32
        dataloader_pin_memory=False,
    )

    # Trainer
    trainer = Trainer(
        model=model,
        args=training_args,
        train_dataset=dataset["train"],
        eval_dataset=dataset["validation"],
        data_collator=data_collator,
        compute_metrics=compute_metrics,
        callbacks=[EarlyStoppingCallback(early_stopping_patience=2)],
    )

    # Train
    print("\n🚀 Starting training...")
    train_result = trainer.train()

    # Print training results
    print("\n📊 Training Results:")
    print(f"   Training loss: {train_result.training_loss:.4f}")
    print(f"   Training samples/sec: {train_result.metrics.get('train_samples_per_second', 0):.1f}")

    # Evaluate on validation set
    print("\n📊 Validation Results:")
    val_metrics = trainer.evaluate()
    for key, value in sorted(val_metrics.items()):
        if isinstance(value, float):
            print(f"   {key}: {value:.4f}")

    # Evaluate on test set
    print("\n📊 Test Results:")
    test_metrics = trainer.evaluate(dataset["test"])
    for key, value in sorted(test_metrics.items()):
        if isinstance(value, float):
            print(f"   {key}: {value:.4f}")

    # Detailed classification report on test set
    print("\n📋 Detailed Test Classification Report:")
    predictions = trainer.predict(dataset["test"])
    pred_ids = np.argmax(predictions.predictions, axis=2)

    true_labels = []
    pred_labels = []
    for pred_seq, label_seq in zip(pred_ids, predictions.label_ids):
        true_seq = []
        pred_seq_labels = []
        for pred_id, label_id in zip(pred_seq, label_seq):
            if label_id == IGNORED_LABEL_ID:
                continue
            true_seq.append(ID_TO_LABEL[label_id])
            pred_seq_labels.append(ID_TO_LABEL[pred_id])
        true_labels.append(true_seq)
        pred_labels.append(pred_seq_labels)

    report_str = seqeval_report(true_labels, pred_labels, digits=4)
    print(report_str)

    # Parse per-entity metrics from seqeval for the summary
    from seqeval.metrics import precision_score, recall_score, f1_score
    entity_types = ["MERCHANT", "AMOUNT", "ACCOUNT", "BALANCE"]
    per_entity_metrics = {}
    for entity in entity_types:
        # Filter to just this entity type
        true_ent = [[t if t.endswith(entity) else "O" for t in seq] for seq in true_labels]
        pred_ent = [[p if p.endswith(entity) else "O" for p in seq] for seq in pred_labels]
        per_entity_metrics[entity] = {
            "precision": round(seqeval_precision(true_ent, pred_ent), 4),
            "recall": round(seqeval_recall(true_ent, pred_ent), 4),
            "f1": round(seqeval_f1(true_ent, pred_ent), 4),
        }

    # Save training summary JSON for easy comparison between runs
    from datetime import datetime
    training_summary = {
        "timestamp": datetime.now().isoformat(),
        "args": vars(args),
        "model": MODEL_NAME,
        "labels": LABEL_LIST,
        "dataset_sizes": {
            "train": len(dataset["train"]),
            "validation": len(dataset["validation"]),
            "test": len(dataset["test"]),
        },
        "training": {
            "loss": round(train_result.training_loss, 4),
            "samples_per_second": round(train_result.metrics.get("train_samples_per_second", 0), 1),
            "epochs_completed": train_result.metrics.get("epoch", args.epochs),
        },
        "validation_metrics": {k: round(v, 4) if isinstance(v, float) else v for k, v in val_metrics.items()},
        "test_metrics": {k: round(v, 4) if isinstance(v, float) else v for k, v in test_metrics.items()},
        "per_entity_test_f1": per_entity_metrics,
    }
    summary_path = output_dir / "training_summary.json"
    with open(summary_path, "w") as f:
        json.dump(training_summary, f, indent=2)
    print(f"\n📋 Training summary saved to: {summary_path}")

    # Save best model
    print(f"\n💾 Saving best model to: {output_dir}")
    output_dir.mkdir(parents=True, exist_ok=True)
    trainer.save_model(str(output_dir))
    tokenizer.save_pretrained(str(output_dir))

    # Save label map alongside model
    label_map_path = output_dir / "ner_label_map.json"
    with open(label_map_path, "w") as f:
        json.dump({
            "labels": LABEL_LIST,
            "id_to_label": ID_TO_LABEL,
            "label_to_id": LABEL_TO_ID,
        }, f, indent=2)
    print(f"   Label map saved to: {label_map_path}")

    # Check success criteria
    test_f1 = test_metrics.get("eval_f1", 0)
    if test_f1 >= 0.85:
        print(f"\n✅ SUCCESS: Test F1 = {test_f1:.4f} (≥ 0.85 threshold)")
    else:
        print(f"\n⚠️  WARNING: Test F1 = {test_f1:.4f} (below 0.85 threshold)")
        print("   Consider: more epochs, learning rate tuning, or additional training data")

    print(f"\nNext step: python convert_to_tflite.py --model {output_dir} --output output/")


if __name__ == "__main__":
    main()
