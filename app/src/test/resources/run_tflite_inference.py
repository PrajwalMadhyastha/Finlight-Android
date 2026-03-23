"""
Minimal TFLite inference bridge for the NER evaluation harness.

Reads tokenized inputs (input_ids, attention_mask) from a .npz file,
runs inference through the TFLite model (with SELECT_TF_OPS/Flex support),
and writes output logits as a .npy file.

Usage (called by DesktopNerExtractor.kt):
    python3 run_tflite_inference.py <model_path> <input_npz_path> <output_npy_path>
"""
import sys
import numpy as np
import tensorflow as tf

def main():
    if len(sys.argv) != 4:
        print("Usage: python3 run_tflite_inference.py <model> <input.npz> <output.npy>", file=sys.stderr)
        sys.exit(1)

    model_path, input_path, output_path = sys.argv[1], sys.argv[2], sys.argv[3]

    # Load tokenized inputs
    data = np.load(input_path)
    input_ids = data["input_ids"].astype(np.int32)          # shape: [1, 128]
    attention_mask = data["attention_mask"].astype(np.int32) # shape: [1, 128]

    # Load TFLite interpreter (supports SELECT_TF_OPS natively on desktop TF)
    interpreter = tf.lite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()

    # Map input tensors by name (model may have them in any order)
    input_details = interpreter.get_input_details()
    for detail in input_details:
        name = detail["name"]
        idx = detail["index"]
        if "input_ids" in name:
            interpreter.set_tensor(idx, input_ids)
        elif "attention_mask" in name:
            interpreter.set_tensor(idx, attention_mask)

    # Run inference
    interpreter.invoke()

    # Get output logits
    output_details = interpreter.get_output_details()
    logits = interpreter.get_tensor(output_details[0]["index"])  # shape: [1, 128, 9]

    # Save output
    np.save(output_path, logits)

if __name__ == "__main__":
    main()
