import os
import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
from peft import PeftModel

base_model_id = "openbmb/MiniCPM-1B-sft-bf16"
adapter_dir = "./minicpm-1b-aegis-adapter"
output_dir = "./merged-minicpm-hf"

print(f"1. Loading base model: {base_model_id}")
# Load in bfloat16 for high fidelity merge without precision loss
tokenizer = AutoTokenizer.from_pretrained(base_model_id, trust_remote_code=True)
base_model = AutoModelForCausalLM.from_pretrained(
    base_model_id,
    device_map="auto",
    torch_dtype=torch.bfloat16,
    trust_remote_code=True
)

print(f"2. Loading LoRA adapter: {adapter_dir}")
# Load PEFT model
model = PeftModel.from_pretrained(base_model, adapter_dir)

print("3. Merging adapters with base weights...")
# Merge LoRA layers back into base parameters
merged_model = model.merge_and_unload()

print(f"4. Saving merged model and tokenizer to: {output_dir}")
merged_model.save_pretrained(output_dir, safe_serialization=True)
tokenizer.save_pretrained(output_dir)

print("\n======================================================================")
print(" SUCCESS: Fine-tuned model merged and saved successfully!")
print("======================================================================")
print("Next steps to convert to GGUF:")
print("1. Clone llama.cpp if you haven't already:")
print("   git clone https://github.com/ggerganov/llama.cpp")
print("   cd llama.cpp")
print("   pip install -r requirements.txt")
print("2. Run the conversion script:")
print("   python convert_hf_to_gguf.py ../merged-minicpm-hf --outfile ../minicpm-aegis.gguf --outtype q4_k_m")
print("======================================================================\n")
