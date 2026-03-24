#!/bin/bash
# ============================================================================
# Phase 4: NER Model Training Environment Setup
# ============================================================================
# Creates a Python virtual environment and installs all dependencies needed
# for the NER training pipeline.
#
# Usage: bash setup.sh
# ============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔧 Setting up NER training environment..."

# Create virtual environment
if [ ! -d "venv" ]; then
    echo "📦 Creating Python virtual environment..."
    python3 -m venv venv
else
    echo "✅ Virtual environment already exists"
fi

# Activate venv
source venv/bin/activate

# Upgrade pip
echo "⬆️  Upgrading pip..."
pip install --upgrade pip -q

# Install dependencies
echo "📥 Installing dependencies..."
pip install -r requirements.txt -q

# Create data and output directories
mkdir -p data
mkdir -p output

echo ""
echo "✅ Environment setup complete!"
echo ""
echo "To activate the environment:"
echo "  source venv/bin/activate"
echo ""
echo "Next steps:"
echo "  1. Place your NER export JSON files in ml_training/data/"
echo "  2. Run: python prepare_data.py --input data/ --output output/dataset/"
echo "  3. Run: python train_ner.py --data output/dataset/ --output output/best_model/"
echo "  4. Run: python convert_to_tflite.py --model output/best_model/ --output output/"
echo "  5. Run: python evaluate.py --model output/best_model/ --test-data output/dataset/"
