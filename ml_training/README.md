# Finlight NER Model Training Pipeline

Local training pipeline to fine-tune MobileBERT for SMS entity extraction (Named Entity Recognition).

## Entities

| Entity | Description | Example |
|---|---|---|
| MERCHANT | Business/payee name | "Amazon", "Swiggy" |
| AMOUNT | Transaction amount | "Rs.1,500.00", "INR 5000" |
| ACCOUNT | Bank account/card | "XX1234", "A/c XXXXX5678" |
| BALANCE | Available balance | "Avl Bal INR 50,000" |

## Prerequisites

- Python 3.8+
- ~4 GB disk space (for model weights + venv)

## Quick Start

```bash
# 1. Setup environment
bash setup.sh
source venv/bin/activate

# 2. Place your NER export JSON files in data/
# cp /path/to/*-ner_training_data.json data/

# 3. Prepare dataset (align tokens to WordPiece)
#    --augment-banks 3 creates 3 synthetic copies per item with different bank names
python prepare_data.py --input data/ --output output/dataset/ --augment-banks 3

# 4. Train model
python train_ner.py --data output/dataset/ --output output/best_model/

# 5. Evaluate
python evaluate.py --model output/best_model/ --test-data output/dataset/

# 6. Convert to TFLite (requires tensorflow + onnx-tf)
pip install tensorflow onnx-tf
python convert_to_tflite.py --model output/best_model/ --output output/
```

## Output Files

| File | Description |
|---|---|
| `output/sms_ner.tflite` | TFLite model for Android (~25 MB) |
| `output/ner_label_map.json` | Label ID → entity type mapping |

## Pipeline Architecture

```
NER Export JSON ──→ prepare_data.py ──→ HuggingFace Dataset
  (your labels)      (WordPiece align)     (BIO labels)
                                               │
                                               ↓
                                        train_ner.py
                                     (MobileBERT fine-tune)
                                               │
                                               ↓
                                      PyTorch checkpoint
                                               │
                                               ↓
                                    convert_to_tflite.py
                                    (ONNX → TFLite FP16)
                                               │
                                               ↓
                                        sms_ner.tflite
                                      (→ Android assets)
```

## Privacy

All training runs **locally**. No data is uploaded. The only network call is a one-time download of the pre-trained MobileBERT model weights (~100 MB) from HuggingFace.
