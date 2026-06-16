import os
import modal

# Define the Modal application name and image requirements.
# We include torch, transformers, peft, trl, bitsandbytes, and accelerate for QLoRA SFT training.
app = modal.App("minicpm-1b-sft-training")

volume = modal.Volume.from_name("minicpm-aegis-volume", create_if_missing=True)

image = (
    modal.Image.debian_slim(python_version="3.10")
    .pip_install(
        "torch",
        "transformers>=4.40.0,<5.0.0",
        "peft",
        "trl",
        "bitsandbytes",
        "accelerate",
        "hf_transfer"
    )
    .env({"HF_HUB_ENABLE_HF_TRANSFER": "1"})
    .add_local_file("./dataset.jsonl", remote_path="/root/dataset.jsonl")
)

@app.function(
    image=image,
    gpu="A10G",          # We request a serverless NVIDIA A10G GPU (24GB VRAM)
    timeout=1200,        # 20-minute execution threshold
    volumes={"/data": volume}
)
def train():
    import torch
    from datasets import load_dataset
    from transformers import (
        AutoModelForCausalLM,
        AutoTokenizer,
        BitsAndBytesConfig
    )
    from peft import LoraConfig, get_peft_model, prepare_model_for_kbit_training
    from trl import SFTTrainer, SFTConfig

    model_id = "openbmb/MiniCPM-1B-sft-bf16"
    print(f"Loading base model: {model_id}")

    # 1. 4-bit Quantization Configuration (QLoRA)
    bnb_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=torch.bfloat16,
        bnb_4bit_use_double_quant=True
    )

    tokenizer = AutoTokenizer.from_pretrained(model_id, trust_remote_code=True)
    tokenizer.pad_token = tokenizer.eos_token

    model = AutoModelForCausalLM.from_pretrained(
        model_id,
        quantization_config=bnb_config,
        device_map="auto",
        trust_remote_code=True
    )
    # 2. PEFT / LoRA target configuration
    lora_config = LoraConfig(
        r=16,
        lora_alpha=32,
        target_modules=["q_proj", "k_proj", "v_proj", "o_proj", "gate_proj", "up_proj", "down_proj"], # Target all linear layers
        lora_dropout=0.05,
        bias="none",
        task_type="CAUSAL_LM"
    )

    # 3. Load dataset
    print("Loading training dataset...")
    # SFTTrainer accepts conversational messages format out-of-the-box
    dataset = load_dataset("json", data_files="/root/dataset.jsonl", split="train")

    # 4. Configure Training Arguments using SFTConfig (fixes trl v1.x max_seq_length TypeError)
    training_args = SFTConfig(
        output_dir="./results",
        num_train_epochs=12,              # Increase epochs to 12 for robust learning of format constraints
        per_device_train_batch_size=2,    # Smaller batch for more updates per epoch
        gradient_accumulation_steps=2,    # Smaller accumulation for more updates per epoch
        learning_rate=2e-4,
        logging_steps=5,
        save_strategy="no",
        fp16=False,
        bf16=True, # Train in bfloat16 precision
        optim="paged_adamw_32bit",
        report_to="none",
        max_length=512
    )

    # 5. Initialize SFTTrainer
    trainer = SFTTrainer(
        model=model,
        train_dataset=dataset,
        peft_config=lora_config,
        processing_class=tokenizer,
        args=training_args
    )

    print("Starting QLoRA Fine-Tuning SFT training...")
    trainer.train()
    print("Training complete!")

    # 6. Save target PEFT adapter weights and tokenizer to persistent volume
    adapter_path = "/data/minicpm-1b-aegis-adapter"
    trainer.model.save_pretrained(adapter_path)
    trainer.tokenizer.save_pretrained(adapter_path)
    print(f"PEFT adapter and tokenizer saved to: {adapter_path}")
    
    # Commit volume changes
    volume.commit()

    return "Fine-tuning completed successfully!"
