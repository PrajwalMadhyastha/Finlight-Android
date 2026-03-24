#!/usr/bin/env python3
"""
prepare_data.py — NER Data Preparation with WordPiece Alignment
================================================================
Loads NER export JSON files from the Finlight labeler, aligns custom tokenizer
labels to MobileBERT's WordPiece tokenizer, and produces a HuggingFace-compatible
dataset for token classification training.

Usage:
    python prepare_data.py --input data/ --output output/dataset/
"""

import argparse
import json
import os
import sys
from pathlib import Path
from typing import List, Dict, Tuple, Optional

from transformers import AutoTokenizer
from datasets import Dataset, DatasetDict


# ============================================================================
# Configuration
# ============================================================================
MODEL_NAME = "google/mobilebert-uncased"
MAX_SEQ_LENGTH = 128

# BIO Label Map (9 labels)
LABEL_LIST = [
    "O",           # 0
    "B-MERCHANT",  # 1
    "I-MERCHANT",  # 2
    "B-AMOUNT",    # 3
    "I-AMOUNT",    # 4
    "B-ACCOUNT",   # 5
    "I-ACCOUNT",   # 6
    "B-BALANCE",   # 7
    "I-BALANCE",   # 8
]
LABEL_TO_ID = {label: idx for idx, label in enumerate(LABEL_LIST)}
IGNORED_LABEL_ID = -100  # PyTorch CrossEntropyLoss ignores this

# Comprehensive list of Indian bank names for data augmentation.
# This teaches the model to recognize any bank name in ACCOUNT contexts.
INDIAN_BANK_NAMES = [
    # Public Sector Banks
    "State Bank of India",
    "Punjab National Bank",
    "Bank of Baroda",
    "Canara Bank",
    "Union Bank of India",
    "Bank of India",
    "Indian Bank",
    "Central Bank of India",
    "Indian Overseas Bank",
    "UCO Bank",
    "Bank of Maharashtra",
    "Punjab and Sind Bank",
    # Major Private Sector Banks
    "HDFC Bank",
    "ICICI Bank",
    "Axis Bank",
    "Kotak Mahindra Bank",
    "IndusInd Bank",
    "Yes Bank",
    "IDBI Bank",
    "Federal Bank",
    "South Indian Bank",
    "RBL Bank",
    "Bandhan Bank",
    "IDFC First Bank",
    "Karur Vysya Bank",
    "City Union Bank",
    "Tamilnad Mercantile Bank",
    "Dhanlaxmi Bank",
    "Jammu and Kashmir Bank",
    "Karnataka Bank",
    "Lakshmi Vilas Bank",
    "Nainital Bank",
    "CSB Bank",
    # Small Finance Banks
    "AU Small Finance Bank",
    "Ujjivan Small Finance Bank",
    "Equitas Small Finance Bank",
    "Jana Small Finance Bank",
    "Suryoday Small Finance Bank",
    "Fincare Small Finance Bank",
    "ESAF Small Finance Bank",
    "North East Small Finance Bank",
    "Capital Small Finance Bank",
    # Payments Banks
    "Paytm Payments Bank",
    "Airtel Payments Bank",
    "India Post Payments Bank",
    "Fino Payments Bank",
    "Jio Payments Bank",
    # Foreign Banks Operating in India
    "Citibank",
    "Standard Chartered Bank",
    "HSBC Bank",
    "Deutsche Bank",
    "DBS Bank",
    "Barclays Bank",
    # Common Short Forms (as they appear in SMS)
    "SBI",
    "PNB",
    "BOB",
    "BOI",
    "IOB",
    "CBI",
]


# ============================================================================
# Core: Load NER Export Data
# ============================================================================
def load_ner_exports(input_dir: str) -> List[dict]:
    """Load all NER export JSON files from the input directory."""
    all_items = []
    input_path = Path(input_dir)

    json_files = list(input_path.glob("*.json"))
    if not json_files:
        print(f"❌ No JSON files found in {input_dir}")
        sys.exit(1)

    for json_file in sorted(json_files):
        print(f"  Loading: {json_file.name}")
        with open(json_file, "r", encoding="utf-8") as f:
            items = json.load(f)
            if isinstance(items, list):
                all_items.extend(items)
            else:
                all_items.append(items)

    print(f"  Total items loaded: {len(all_items)}")
    return all_items


# ============================================================================
# Core: Character-Level Span Reconstruction
# ============================================================================
def reconstruct_char_spans(text: str, custom_tokens: List[str]) -> List[Tuple[int, int]]:
    """
    Reconstruct character-level (start, end) spans for each custom token
    by finding them sequentially in the original text.

    This handles the case where the custom tokenizer splits on whitespace
    and mixed content, and we need to map back to exact character positions.
    """
    spans = []
    search_start = 0

    for token in custom_tokens:
        # Find this token in the text starting from where we left off
        idx = text.find(token, search_start)
        if idx == -1:
            # Try case-insensitive search as fallback
            idx = text.lower().find(token.lower(), search_start)
        if idx == -1:
            # Token not found in text — this shouldn't happen if tokenizer is consistent
            # Use the current position as a best guess
            print(f"  ⚠️  Token '{token}' not found in text at position {search_start}")
            idx = search_start

        spans.append((idx, idx + len(token)))
        search_start = idx + len(token)

    return spans


def build_entity_char_spans(
    item: dict, custom_token_spans: List[Tuple[int, int]]
) -> List[Dict]:
    """
    Convert entity token indices to character-level spans.

    Handles non-contiguous token indices (e.g., ACCOUNT spanning
    "SB A/c 12345" at tokens [1,2,3] AND "Union Bank of India" at
    tokens [21,22,23,24]) by producing multiple character spans per entity.

    Output per entity:
        {"type": "ACCOUNT", "spans": [(s1,e1), (s2,e2)], "text": "..."}
    """
    entity_char_spans = []

    for entity in item.get("entities", []):
        token_indices = entity["tokenIndices"]
        if not token_indices:
            continue

        # Sort indices to get positional order in the text
        sorted_indices = sorted(token_indices)

        # Group into contiguous runs
        # e.g., [1, 2, 3, 21, 22, 23, 24] → [[1,2,3], [21,22,23,24]]
        groups = []
        current_group = [sorted_indices[0]]
        for k in range(1, len(sorted_indices)):
            if sorted_indices[k] == current_group[-1] + 1:
                current_group.append(sorted_indices[k])
            else:
                groups.append(current_group)
                current_group = [sorted_indices[k]]
        groups.append(current_group)

        # Convert each contiguous group to a character span
        spans = []
        for group in groups:
            char_start = custom_token_spans[group[0]][0]
            char_end = custom_token_spans[group[-1]][1]
            spans.append((char_start, char_end))

        # Sort spans by position in text (already sorted since indices were sorted)
        spans.sort(key=lambda s: s[0])

        entity_char_spans.append({
            "type": entity["type"],
            "spans": spans,
            "text": entity["text"],
        })

    return entity_char_spans


# ============================================================================
# Core: WordPiece Alignment
# ============================================================================
def align_labels_to_wordpiece(
    text: str,
    entity_char_spans: List[Dict],
    tokenizer,
    max_length: int = MAX_SEQ_LENGTH,
) -> Dict:
    """
    Tokenize text with WordPiece and assign BIO labels based on character spans.
    Handles entities with multiple disjoint spans (non-contiguous tokens).

    Returns a dict with input_ids, attention_mask, and labels ready for training.
    """
    # Tokenize with offset mapping so we know which characters each sub-token covers
    encoding = tokenizer(
        text,
        max_length=max_length,
        padding="max_length",
        truncation=True,
        return_offsets_mapping=True,
        return_tensors=None,  # Return plain lists
    )

    offset_mapping = encoding["offset_mapping"]
    labels = [IGNORED_LABEL_ID] * len(encoding["input_ids"])

    # Pre-compute: for each entity, track which span each sub-token belongs to.
    # Each contiguous span group gets its own B- tag at its first sub-token.
    # This ensures non-contiguous entities produce multiple extractable spans
    # that can be merged in post-processing.
    #
    # Example for ACCOUNT with spans [(10,25), (95,114)]:
    #   "SB"    → B-ACCOUNT  (first token of span 1)
    #   "A/c"   → I-ACCOUNT
    #   "12345" → I-ACCOUNT
    #   ...gap...
    #   "Union" → B-ACCOUNT  (first token of span 2)
    #   "Bank"  → I-ACCOUNT
    #   "of"    → I-ACCOUNT
    #   "India" → I-ACCOUNT

    # Track which (entity_idx, span_idx) pairs have had their B- tag assigned
    span_started = set()

    # For each sub-token, check if it overlaps with any entity span
    for idx, (token_start, token_end) in enumerate(offset_mapping):
        # Special tokens ([CLS], [SEP], [PAD]) have offset (0, 0) — keep as IGNORED
        if token_start == 0 and token_end == 0:
            continue

        # Default: O (outside any entity)
        labels[idx] = LABEL_TO_ID["O"]

        # Check overlap with each entity's spans
        for ent_idx, entity in enumerate(entity_char_spans):
            e_type = entity["type"]
            matched = False

            for span_idx, (span_start, span_end) in enumerate(entity["spans"]):
                # Check if this sub-token falls within this span
                if token_start >= span_start and token_end <= span_end:
                    span_key = (ent_idx, span_idx)
                    if span_key not in span_started:
                        # First sub-token of this span group → B- tag
                        labels[idx] = LABEL_TO_ID[f"B-{e_type}"]
                        span_started.add(span_key)
                    else:
                        # Continuation sub-token within same span → I- tag
                        labels[idx] = LABEL_TO_ID[f"I-{e_type}"]
                    matched = True
                    break  # Found the span, no need to check other spans

            if matched:
                break  # A token can only belong to one entity

    return {
        "input_ids": encoding["input_ids"],
        "attention_mask": encoding["attention_mask"],
        "labels": labels,
    }


# ============================================================================
# Core: Process All Items
# ============================================================================
def process_items(
    items: List[dict], tokenizer
) -> Tuple[List[Dict], int, int]:
    """
    Process all NER export items into training examples.
    Returns (examples, success_count, skip_count).
    """
    examples = []
    success_count = 0
    skip_count = 0

    for item in items:
        text = item.get("text", "")
        custom_tokens = item.get("tokens", [])
        entities = item.get("entities", [])

        if not text or not custom_tokens:
            skip_count += 1
            continue

        # Skip items with no labeled entities (they're still useful as negative examples)
        # but we'll include them — the model needs to learn "O" labels too

        try:
            # Step 1: Reconstruct character spans for custom tokens
            custom_token_spans = reconstruct_char_spans(text, custom_tokens)

            # Step 2: Convert entity token indices to character spans
            entity_char_spans = build_entity_char_spans(item, custom_token_spans)

            # Step 3: Align to WordPiece and generate BIO labels
            aligned = align_labels_to_wordpiece(
                text, entity_char_spans, tokenizer
            )

            examples.append(aligned)
            success_count += 1

        except Exception as e:
            print(f"  ⚠️  Skipping item {item.get('id', '?')}: {e}")
            skip_count += 1

    return examples, success_count, skip_count


# ============================================================================
# Data Augmentation: Bank Name Swapping
# ============================================================================
def find_bank_in_text(text: str, bank_list: List[str]) -> Optional[Tuple[str, int, int]]:
    """
    Find a known bank name in the given text.
    Returns (bank_name, start_idx, end_idx) or None.
    Searches longest names first to avoid partial matches.
    """
    # Sort by length descending to match longest first
    # (e.g., "State Bank of India" before "Bank of India" before "India")
    sorted_banks = sorted(bank_list, key=len, reverse=True)

    for bank in sorted_banks:
        idx = text.lower().find(bank.lower())
        if idx != -1:
            return bank, idx, idx + len(bank)

    return None


def augment_with_bank_names(
    items: List[dict],
    tokenizer,
    bank_list: List[str],
    augmentations_per_item: int = 3,
) -> Tuple[List[Dict], int]:
    """
    Create augmented training examples by swapping bank names in ACCOUNT entities.

    For each item with an ACCOUNT entity containing a recognized bank name,
    creates N synthetic copies with different bank names inserted.

    Works at the character-span level:
    1. Find the bank name in the original text
    2. Replace with a new bank name
    3. Shift all character spans accordingly
    4. Pass the modified text + adjusted spans directly to WordPiece alignment

    Returns (augmented_examples, count).
    """
    import random

    augmented = []
    aug_count = 0

    for item in items:
        text = item.get("text", "")
        custom_tokens = item.get("tokens", [])
        entities = item.get("entities", [])

        if not text or not custom_tokens or not entities:
            continue

        # Check if any ACCOUNT entity exists
        has_account = any(e["type"] == "ACCOUNT" for e in entities)
        if not has_account:
            continue

        # Reconstruct character spans
        try:
            custom_token_spans = reconstruct_char_spans(text, custom_tokens)
            entity_char_spans = build_entity_char_spans(item, custom_token_spans)
        except Exception:
            continue

        # Find a bank name anywhere in the text
        bank_match = find_bank_in_text(text, bank_list)
        if bank_match is None:
            continue

        old_bank, old_start, old_end = bank_match

        # Verify the bank name falls within an ACCOUNT entity span
        bank_in_account = False
        for entity in entity_char_spans:
            if entity["type"] != "ACCOUNT":
                continue
            for s, e in entity["spans"]:
                if old_start >= s and old_end <= e:
                    bank_in_account = True
                    break
            if bank_in_account:
                break

        if not bank_in_account:
            continue

        # Create augmented copies with different bank names
        used_banks = {old_bank.lower()}
        attempts = 0
        copies_made = 0

        while copies_made < augmentations_per_item and attempts < augmentations_per_item * 3:
            attempts += 1
            new_bank = random.choice(bank_list)
            if new_bank.lower() in used_banks:
                continue
            used_banks.add(new_bank.lower())

            # Build new text with bank name swapped
            new_text = text[:old_start] + new_bank + text[old_end:]
            delta = len(new_bank) - len(old_bank)

            # Adjust all entity spans
            new_entity_spans = []
            for entity in entity_char_spans:
                new_spans = []
                for s, e in entity["spans"]:
                    if e <= old_start:
                        # Entirely before replacement — no change
                        new_spans.append((s, e))
                    elif s >= old_end:
                        # Entirely after replacement — shift by delta
                        new_spans.append((s + delta, e + delta))
                    elif s >= old_start and e <= old_end:
                        # This IS the bank name span — resize to new bank
                        new_spans.append((old_start, old_start + len(new_bank)))
                    else:
                        # Partially overlapping — expand to cover
                        new_s = min(s, old_start)
                        new_e = max(e + delta, old_start + len(new_bank))
                        new_spans.append((new_s, new_e))

                new_entity_spans.append({
                    "type": entity["type"],
                    "spans": new_spans,
                    "text": entity["text"],
                })

            # Align to WordPiece and create training example
            try:
                aligned = align_labels_to_wordpiece(
                    new_text, new_entity_spans, tokenizer
                )
                augmented.append(aligned)
                copies_made += 1
                aug_count += 1
            except Exception:
                pass  # Skip failed augmentation

    return augmented, aug_count


# ============================================================================
# Validation: Print Sample Alignments
# ============================================================================
def print_sample_alignments(
    items: List[dict],
    tokenizer,
    num_samples: int = 5,
):
    """Print sample alignments for visual verification."""
    print("\n" + "=" * 80)
    print("SAMPLE ALIGNMENTS (for manual verification)")
    print("=" * 80)

    # Pick items that have entities
    items_with_entities = [i for i in items if i.get("entities")]
    samples = items_with_entities[:num_samples]

    for i, item in enumerate(samples):
        text = item["text"]
        custom_tokens = item["tokens"]
        entities = item.get("entities", [])

        # Reconstruct spans
        custom_token_spans = reconstruct_char_spans(text, custom_tokens)
        entity_char_spans = build_entity_char_spans(item, custom_token_spans)

        # Tokenize with WordPiece
        encoding = tokenizer(
            text,
            max_length=MAX_SEQ_LENGTH,
            truncation=True,
            return_offsets_mapping=True,
        )
        wp_tokens = tokenizer.convert_ids_to_tokens(encoding["input_ids"])
        offsets = encoding["offset_mapping"]

        # Get aligned labels
        aligned = align_labels_to_wordpiece(text, entity_char_spans, tokenizer)

        print(f"\n--- Sample {i + 1} ---")
        print(f"Text: {text[:120]}{'...' if len(text) > 120 else ''}")
        print(f"Custom tokens ({len(custom_tokens)}): {custom_tokens[:15]}{'...' if len(custom_tokens) > 15 else ''}")

        # Show entities
        for entity in entities:
            print(f"  Entity: {entity['type']} = \"{entity['text']}\" (token indices: {entity['tokenIndices']})")

        # Show WordPiece tokens with labels (non-special, non-padding only)
        print(f"WordPiece alignment:")
        for j, (token, label_id) in enumerate(zip(wp_tokens, aligned["labels"])):
            if token in ("[CLS]", "[SEP]", "[PAD]"):
                continue
            label_str = LABEL_LIST[label_id] if label_id != IGNORED_LABEL_ID else "[IGN]"
            if label_str != "O":
                print(f"  [{j:3d}] {token:20s} → {label_str}")

        # Verify: reconstruct entity text from all spans
        print(f"Entity verification:")
        for entity in entity_char_spans:
            # Reconstruct from all spans (handles non-contiguous entities)
            span_texts = [text[s:e] for s, e in entity["spans"]]
            reconstructed = " ".join(span_texts)
            original = entity["text"]
            # Normalize for comparison: lowercase, collapse whitespace
            norm_recon = " ".join(reconstructed.lower().split())
            norm_orig = " ".join(original.lower().split())

            if norm_recon == norm_orig:
                match = "✓"
            else:
                # Strip punctuation (* . : / etc.) for fuzzy word-set comparison
                import re
                strip = lambda s: re.sub(r'[^a-z0-9\s]', '', s)
                words_recon = set(strip(norm_recon).split())
                words_orig = set(strip(norm_orig).split())
                if words_recon == words_orig:
                    match = "~ (same words, order/punctuation differs)"
                elif words_orig.issubset(words_recon) or words_recon.issubset(words_orig):
                    match = "~ (partial overlap)"
                else:
                    match = "✗"

            n_spans = len(entity["spans"])
            span_info = f" ({n_spans} spans)" if n_spans > 1 else ""
            print(f"  {entity['type']}: \"{reconstructed}\"{span_info} vs original \"{original}\" {match}")


# ============================================================================
# Dataset Statistics
# ============================================================================
def print_dataset_stats(examples: List[Dict], split_name: str):
    """Print statistics about entity distribution in the dataset."""
    entity_counts = {label: 0 for label in LABEL_LIST if label.startswith("B-")}
    total_tokens = 0
    labeled_tokens = 0

    for ex in examples:
        for label_id in ex["labels"]:
            if label_id == IGNORED_LABEL_ID:
                continue
            total_tokens += 1
            label = LABEL_LIST[label_id]
            if label.startswith("B-"):
                entity_counts[label] += 1
            if label != "O":
                labeled_tokens += 1

    print(f"\n📊 {split_name} set statistics:")
    print(f"   Examples: {len(examples)}")
    print(f"   Total tokens: {total_tokens:,}")
    print(f"   Labeled tokens: {labeled_tokens:,} ({100*labeled_tokens/max(total_tokens,1):.1f}%)")
    for label, count in sorted(entity_counts.items()):
        entity_type = label.replace("B-", "")
        print(f"   {entity_type}: {count:,} entities")


# ============================================================================
# Main
# ============================================================================
def main():
    parser = argparse.ArgumentParser(description="Prepare NER data for MobileBERT training")
    parser.add_argument("--input", required=True, help="Directory containing NER export JSON files")
    parser.add_argument("--output", required=True, help="Directory to save processed HuggingFace dataset")
    parser.add_argument("--train-ratio", type=float, default=0.8, help="Train split ratio (default: 0.8)")
    parser.add_argument("--val-ratio", type=float, default=0.1, help="Validation split ratio (default: 0.1)")
    parser.add_argument("--samples", type=int, default=5, help="Number of alignment samples to print")
    parser.add_argument("--augment-banks", type=int, default=0, metavar="N",
                        help="Augment N synthetic copies per item by swapping bank names (0=off, recommended: 3-5)")
    args = parser.parse_args()

    test_ratio = 1.0 - args.train_ratio - args.val_ratio
    assert test_ratio > 0, "Train + val ratio must be less than 1.0"

    print("=" * 60)
    print("NER Data Preparation for MobileBERT")
    print("=" * 60)

    # Load tokenizer
    print(f"\n📥 Loading tokenizer: {MODEL_NAME}")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)

    # Load NER export data
    print(f"\n📂 Loading NER exports from: {args.input}")
    items = load_ner_exports(args.input)

    # Print sample alignments for verification
    print_sample_alignments(items, tokenizer, num_samples=args.samples)

    # Process all items
    print(f"\n⚙️  Processing {len(items)} items...")
    examples, success, skipped = process_items(items, tokenizer)
    print(f"   ✅ Processed: {success}")
    print(f"   ⚠️  Skipped: {skipped}")

    if not examples:
        print("❌ No valid examples produced. Check your input data.")
        sys.exit(1)

    # Bank name augmentation
    if args.augment_banks > 0:
        print(f"\n🏦 Bank name augmentation (N={args.augment_banks} per eligible item)...")
        print(f"   Bank list: {len(INDIAN_BANK_NAMES)} banks")
        aug_examples, aug_count = augment_with_bank_names(
            items, tokenizer, INDIAN_BANK_NAMES,
            augmentations_per_item=args.augment_banks,
        )
        print(f"   ✅ Generated {aug_count} augmented examples")
        examples.extend(aug_examples)
        print(f"   Total examples: {len(examples)} (original + augmented)")

    # Shuffle and split
    import random
    random.seed(42)
    random.shuffle(examples)

    n = len(examples)
    train_end = int(n * args.train_ratio)
    val_end = train_end + int(n * args.val_ratio)

    train_examples = examples[:train_end]
    val_examples = examples[train_end:val_end]
    test_examples = examples[val_end:]

    print(f"\n📊 Split: train={len(train_examples)}, val={len(val_examples)}, test={len(test_examples)}")

    # Print stats for each split
    print_dataset_stats(train_examples, "Train")
    print_dataset_stats(val_examples, "Validation")
    print_dataset_stats(test_examples, "Test")

    # Convert to HuggingFace Dataset format
    def list_of_dicts_to_dict_of_lists(lst):
        if not lst:
            return {"input_ids": [], "attention_mask": [], "labels": []}
        return {
            "input_ids": [x["input_ids"] for x in lst],
            "attention_mask": [x["attention_mask"] for x in lst],
            "labels": [x["labels"] for x in lst],
        }

    dataset_dict = DatasetDict({
        "train": Dataset.from_dict(list_of_dicts_to_dict_of_lists(train_examples)),
        "validation": Dataset.from_dict(list_of_dicts_to_dict_of_lists(val_examples)),
        "test": Dataset.from_dict(list_of_dicts_to_dict_of_lists(test_examples)),
    })

    # Save to disk
    output_path = Path(args.output)
    output_path.mkdir(parents=True, exist_ok=True)
    dataset_dict.save_to_disk(str(output_path))

    # Also save the label map
    label_map_path = output_path / "label_map.json"
    with open(label_map_path, "w") as f:
        json.dump({"labels": LABEL_LIST, "label_to_id": LABEL_TO_ID}, f, indent=2)

    print(f"\n✅ Dataset saved to: {output_path}")
    print(f"   Label map saved to: {label_map_path}")
    print("\nNext step: python train_ner.py --data output/dataset/ --output output/best_model/")


if __name__ == "__main__":
    main()
