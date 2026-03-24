#!/usr/bin/env python3
"""
evaluate.py — Evaluate NER Model and Report Per-Entity Metrics
================================================================
Evaluates the trained NER model on the test set, applies post-processing
normalization, and prints detailed metrics and sample predictions.

Usage:
    python evaluate.py --model output/best_model/ --test-data output/dataset/
"""

import argparse
import json
import re
import sys
from pathlib import Path
from typing import List, Dict, Optional, Tuple

import numpy as np
import torch
from datasets import load_from_disk
from transformers import AutoModelForTokenClassification, AutoTokenizer
from seqeval.metrics import classification_report as seqeval_report


# ============================================================================
# Configuration
# ============================================================================
MAX_SEQ_LENGTH = 128
IGNORED_LABEL_ID = -100

LABEL_LIST = [
    "O",
    "B-MERCHANT", "I-MERCHANT",
    "B-AMOUNT", "I-AMOUNT",
    "B-ACCOUNT", "I-ACCOUNT",
    "B-BALANCE", "I-BALANCE",
]
ID_TO_LABEL = {i: label for i, label in enumerate(LABEL_LIST)}


# ============================================================================
# Entity Extraction from Predictions
# ============================================================================
# Special tokens that should never be part of an entity
SPECIAL_TOKENS = {"[PAD]", "[SEP]", "[CLS]", "[UNK]", "[MASK]"}


def extract_entities_from_predictions(
    tokens: List[str],
    label_ids: List[int],
    ignore_id: int = IGNORED_LABEL_ID,
) -> List[Dict]:
    """
    Extract structured entities from BIO-tagged token sequences.
    Returns a list of dicts with type, text, and token indices.
    """
    entities = []
    current_entity = None

    for i, (token, label_id) in enumerate(zip(tokens, label_ids)):
        if label_id == ignore_id:
            continue

        # Skip special tokens — model predictions on these are meaningless
        if token in SPECIAL_TOKENS:
            if current_entity:
                entities.append(current_entity)
                current_entity = None
            continue

        label = ID_TO_LABEL.get(label_id, "O")

        if label.startswith("B-"):
            # Save current entity if exists
            if current_entity:
                entities.append(current_entity)

            entity_type = label[2:]
            # Handle WordPiece sub-tokens
            text = token.replace("##", "")
            current_entity = {
                "type": entity_type,
                "text": text,
                "token_indices": [i],
            }

        elif label.startswith("I-") and current_entity:
            entity_type = label[2:]
            if entity_type == current_entity["type"]:
                # Continue current entity
                if token.startswith("##"):
                    current_entity["text"] += token[2:]
                else:
                    current_entity["text"] += " " + token
                current_entity["token_indices"].append(i)
            else:
                # Type mismatch — close current and start new
                entities.append(current_entity)
                text = token.replace("##", "")
                current_entity = {
                    "type": entity_type,
                    "text": text,
                    "token_indices": [i],
                }
        else:
            if current_entity:
                entities.append(current_entity)
                current_entity = None

    if current_entity:
        entities.append(current_entity)

    return entities


def merge_same_type_entities(entities: List[Dict]) -> List[Dict]:
    """
    Merge consecutive entities of the same type into one.

    Non-contiguous entities (e.g., ACCOUNT spanning "SB A/c 12345" and
    "Union Bank of India") produce multiple B-ACCOUNT spans. This function
    merges them back into a single entity for display and downstream use.

    The merged text is in text-position order (left-to-right as in the SMS).
    In Phase 5 (MlSmsParser), reordering can be applied if needed.
    """
    if not entities:
        return entities

    merged = []
    i = 0
    while i < len(entities):
        current = {
            "type": entities[i]["type"],
            "text": entities[i]["text"],
            "token_indices": list(entities[i]["token_indices"]),
        }

        # Look ahead: merge subsequent entities of the same type
        j = i + 1
        while j < len(entities) and entities[j]["type"] == current["type"]:
            if entities[j]["text"].startswith("##"):
                current["text"] += entities[j]["text"][2:]
            else:
                current["text"] += " " + entities[j]["text"]
            current["token_indices"].extend(entities[j]["token_indices"])
            j += 1

        merged.append(current)
        i = j

    return merged


# ============================================================================
# Post-Processing Normalization
# ============================================================================
def normalize_amount(raw_text: str) -> Tuple[str, Optional[float]]:
    """
    Normalize extracted amount/balance text to a numeric value.
    Strips currency prefixes, trailing dots, and parses the number.
    Handles WordPiece spacing artifacts (e.g., "rs . 499 . 79").
    """
    cleaned = raw_text.strip()

    # Fix WordPiece spacing: collapse spaces around dots and commas
    # "rs . 499 . 79" → "rs.499.79"
    # "1 , 773 . 77" → "1,773.77"
    cleaned = re.sub(r'\s*\.\s*', '.', cleaned)
    cleaned = re.sub(r'\s*,\s*', ',', cleaned)

    # Remove common currency prefixes/suffixes
    currency_patterns = [
        r'^(INR|Rs\.?|₹|rs\.?)\s*',  # prefix
        r'\s*(INR|Rs\.?|₹|rs\.?)$',   # suffix
    ]
    for pattern in currency_patterns:
        cleaned = re.sub(pattern, '', cleaned, flags=re.IGNORECASE)

    # Remove "bal :" or "bal:" prefix for balance values
    cleaned = re.sub(r'^(avl\.?\s*)?bal(ance)?\s*:?\s*', '', cleaned, flags=re.IGNORECASE)

    # Remove another round of currency after stripping "bal:"
    for pattern in currency_patterns:
        cleaned = re.sub(pattern, '', cleaned, flags=re.IGNORECASE)

    # Remove trailing dots/dashes and CR suffix
    cleaned = cleaned.rstrip('.-')
    cleaned = re.sub(r'\s*CR$', '', cleaned, flags=re.IGNORECASE)

    # Remove commas (Indian number format: 1,00,000)
    cleaned = cleaned.replace(',', '')

    cleaned = cleaned.strip()

    # Try to parse as float
    try:
        value = float(cleaned)
        return cleaned, value
    except ValueError:
        return cleaned, None


# ============================================================================
# Main Evaluation
# ============================================================================
def main():
    parser = argparse.ArgumentParser(description="Evaluate NER model")
    parser.add_argument("--model", required=True, help="Path to trained model directory")
    parser.add_argument("--test-data", required=True, help="Path to dataset directory (with test split)")
    parser.add_argument("--num-samples", type=int, default=20, help="Number of sample predictions to print")
    args = parser.parse_args()

    print("=" * 60)
    print("NER Model Evaluation")
    print("=" * 60)

    # Load model and tokenizer
    print(f"\n📥 Loading model from: {args.model}")
    tokenizer = AutoTokenizer.from_pretrained(args.model)
    model = AutoModelForTokenClassification.from_pretrained(args.model)
    model.eval()

    # Load test dataset
    print(f"📂 Loading test data from: {args.test_data}")
    dataset = load_from_disk(args.test_data)
    test_dataset = dataset["test"]
    print(f"   Test examples: {len(test_dataset)}")

    # Run predictions
    print("\n⚙️  Running predictions...")
    all_true_labels = []
    all_pred_labels = []
    sample_outputs = []

    amount_total = 0
    amount_parseable = 0
    balance_total = 0
    balance_parseable = 0

    for idx in range(len(test_dataset)):
        example = test_dataset[idx]
        input_ids = torch.tensor([example["input_ids"]])
        attention_mask = torch.tensor([example["attention_mask"]])
        true_label_ids = example["labels"]

        # Model inference
        with torch.no_grad():
            output = model(input_ids=input_ids, attention_mask=attention_mask)
            pred_label_ids = torch.argmax(output.logits, dim=-1)[0].tolist()

        # Convert to label strings (ignoring special tokens)
        true_seq = []
        pred_seq = []
        tokens = tokenizer.convert_ids_to_tokens(example["input_ids"])

        for pred_id, label_id, token in zip(pred_label_ids, true_label_ids, tokens):
            if label_id == IGNORED_LABEL_ID:
                continue
            true_seq.append(ID_TO_LABEL[label_id])
            pred_seq.append(ID_TO_LABEL[pred_id])

        all_true_labels.append(true_seq)
        all_pred_labels.append(pred_seq)

        # Extract entities for sample display and post-processing validation
        pred_entities_raw = extract_entities_from_predictions(tokens, pred_label_ids)
        true_entities_raw = extract_entities_from_predictions(tokens, true_label_ids)

        # Merge non-contiguous spans of the same type
        pred_entities = merge_same_type_entities(pred_entities_raw)
        true_entities = merge_same_type_entities(true_entities_raw)

        # Post-processing validation for amounts/balances
        for entity in pred_entities:
            if entity["type"] == "AMOUNT":
                amount_total += 1
                _, val = normalize_amount(entity["text"])
                if val is not None:
                    amount_parseable += 1
            elif entity["type"] == "BALANCE":
                balance_total += 1
                _, val = normalize_amount(entity["text"])
                if val is not None:
                    balance_parseable += 1

        # Collect samples
        if len(sample_outputs) < args.num_samples:
            text = tokenizer.decode(example["input_ids"], skip_special_tokens=True)
            sample_outputs.append({
                "text": text,
                "true_entities": true_entities,
                "pred_entities": pred_entities,
            })

    # Print classification report
    print("\n" + "=" * 60)
    print("CLASSIFICATION REPORT (Per-Entity)")
    print("=" * 60)
    print(seqeval_report(all_true_labels, all_pred_labels, digits=4))

    # Post-processing validation
    print("=" * 60)
    print("POST-PROCESSING VALIDATION")
    print("=" * 60)
    if amount_total > 0:
        print(f"  AMOUNT:  {amount_parseable}/{amount_total} parseable to float ({100*amount_parseable/amount_total:.1f}%)")
    if balance_total > 0:
        print(f"  BALANCE: {balance_parseable}/{balance_total} parseable to float ({100*balance_parseable/balance_total:.1f}%)")

    # Print sample predictions
    print("\n" + "=" * 60)
    print(f"SAMPLE PREDICTIONS ({len(sample_outputs)} examples)")
    print("=" * 60)

    for i, sample in enumerate(sample_outputs):
        print(f"\n--- Sample {i + 1} ---")
        print(f"Text: {sample['text'][:120]}{'...' if len(sample['text']) > 120 else ''}")

        # True entities
        if sample["true_entities"]:
            print("  True entities:")
            for e in sample["true_entities"]:
                print(f"    {e['type']:12s} → \"{e['text']}\"")
        else:
            print("  True entities: (none)")

        # Predicted entities
        if sample["pred_entities"]:
            print("  Pred entities:")
            for e in sample["pred_entities"]:
                suffix = ""
                if e["type"] in ("AMOUNT", "BALANCE"):
                    _, val = normalize_amount(e["text"])
                    suffix = f" → {val}" if val is not None else " → ⚠️ unparseable"
                print(f"    {e['type']:12s} → \"{e['text']}\"{suffix}")
        else:
            print("  Pred entities: (none)")

        # Quick match check
        true_set = {(e["type"], e["text"]) for e in sample["true_entities"]}
        pred_set = {(e["type"], e["text"]) for e in sample["pred_entities"]}
        if true_set == pred_set:
            print("  ✅ Perfect match")
        else:
            missed = true_set - pred_set
            extra = pred_set - true_set
            if missed:
                print(f"  ❌ Missed: {missed}")
            if extra:
                print(f"  ➕ Extra:  {extra}")

    print(f"\n✅ Evaluation complete!")


if __name__ == "__main__":
    main()
