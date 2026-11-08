# Changelog

## 0.1.0

First release for JetBrains IDEs (build 252 and newer).

- Opens .gguf, .safetensors, .npy and .npz files, plus PyTorch .pt, .pth and .ckpt files and pytorch_model .bin files when their first bytes show a PyTorch archive or pickle.
- Tensor list with name, dtype or GGUF quantization type, shape and size. Search and sort.
- GGUF model facts (architecture, context length, layers, heads, tokenizer, file type, bits per weight) and every metadata key.
- Safetensors header and its __metadata__. Parameter count for every format.
- Stats, exact median and percentiles, histogram, heatmap, image view and a paged value table for the tensor you pick. Save as .npy or CSV.
- Reads by byte range only. A 20 GB GGUF opens from its header.
- Without JCEF (remote development, some Linux setups) the tab shows the GGUF or safetensors header parsed in Kotlin and an "Open in Default App" button.
