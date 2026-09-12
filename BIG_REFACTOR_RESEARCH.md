# Manga OCR research notes

## Findings

The selected Manga OCR ONNX architecture is a vision encoder-decoder: the encoder accepts `pixel_values` shaped `[1,3,224,224]` and returns `last_hidden_state`; the decoder accepts `input_ids` and `encoder_hidden_states` and returns `logits`. The model family uses a BERT-style WordPiece vocabulary and special tokens; these IDs must come from model configuration, not assumptions.

## Crop strategy decision

A fixed `224x224` crop is **not** the size of the original manga speech bubble. It is only the model's normalized input size. The correct pipeline is:

1. Detect a candidate text region/bubble using ML Kit geometry.
2. Expand the candidate rectangle with bounded padding.
3. Preserve the complete candidate aspect ratio while resizing to the model input. Do not crop the candidate to a square.
4. Pad the resized image to the model's required square input using a neutral background when necessary.
5. Keep the original rectangle for the translation overlay.

This avoids cutting off wide, tall, or irregular bubbles. A single ML Kit line box is insufficient for Manga OCR; a paragraph/block or merged nearby text region should be passed when possible.

## Required implementation safeguards

- Inspect ONNX session metadata and use actual input/output names.
- Read BOS/EOS/PAD/UNK IDs from `generation_config.json`, `config.json`, or `special_tokens_map.json`; fail clearly if unavailable.
- Use the tokenizer's actual vocabulary format and preserve WordPiece continuation behavior.
- Bound generation length and stop on EOS.
- Keep model loading off the main thread and release temporary bitmaps/tensors deterministically.
- Do not mark the stage complete until a real device test confirms model download, session creation, inference, and overlay output.
