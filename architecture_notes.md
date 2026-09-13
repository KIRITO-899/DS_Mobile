# MobileNetV4 Architecture Notes

These are my notes on how the MobileNetV4 variants we're using are put together, mainly so I actually understand what I'm loading via `timm` and not just treating it as a black box. Writing this up for the mid-eval too.

Since our plan changed, we're no longer just using Conv-Small — we're comparing **two** MobileNetV4 variants against each other (Conv-Small vs. Hybrid-Medium) plus MobileNetV2 as an older-generation baseline. Updating these notes to reflect that.

## Why MobileNetV4 is different — the UIB block

The main thing MobileNetV4 does differently from V2/V3 is that it doesn't use one fixed block design everywhere. Instead it has this one flexible block called the **Universal Inverted Bottleneck (UIB)**, and depending on which parts of it are switched on or off, it can basically turn into a few different block types that older architectures used separately.

A UIB block is built from these pieces, in order:

`Depthwise Start -> Pointwise Expansion -> Depthwise Middle -> SE (Squeeze-and-Excitation) -> Pointwise Projection -> Depthwise End`

Any of the depthwise steps can just be an identity (i.e. skipped), and that's what changes the block's "personality":

- If **both** `dw_start` and `dw_mid` are active, you get what the paper calls **ExtraDW**. Basically two rounds of cheap spatial filtering, which helps the network see a wider area without spending much compute.
- If only `dw_mid` is active and `dw_start` is skipped, that's just the regular **Inverted Bottleneck** block you'd recognize from MobileNetV2/V3.
- If `dw_start` is active but `dw_mid` is skipped, that's closer to a **ConvNeXt-style** block — spatial mixing happens before the channels get expanded, instead of after.

I actually saw this directly when I printed out the model in my notebook — some blocks show `dw_start` as `Identity()` and others show `dw_mid` as `Identity()`, which lines up exactly with what I just described. So this isn't just a diagram in the paper, it's really how the model is built layer by layer. This applies to **both** Conv-Small and Hybrid-Medium — UIB is the shared foundation across the whole MobileNetV4 family.

The reason this matters (from what I understand): letting a NAS search pick and mix these variants per-layer means the network can put "expensive" blocks only where they're worth it and cheap ones everywhere else, instead of using the same block shape throughout like older models did.

## Mobile MQA — this time it actually matters for us

Earlier I'd written that Mobile MQA doesn't apply to our project, back when we were only using Conv-Small. That's changed now that Hybrid-Medium is part of our comparison, so this section needed a rewrite.

Mobile MQA (Mobile Multi-Query Attention) is a lightweight attention mechanism the paper adds on top of UIB blocks in the **Hybrid** variants. It shares keys and values across attention heads (instead of each head having its own), which cuts down memory access cost and latency compared to standard multi-head self-attention — that's the whole point of it being "mobile."

It typically shows up in the later stages of the network, once the feature maps have already been downsampled by the earlier UIB blocks — attention is expensive per-pixel, so applying it only once the resolution has shrunk keeps the cost manageable.

**Why this matters for us now:** this is no longer just background reading — it's the actual variable our project is testing. We picked Conv-Small (UIB only, no MQA) and Hybrid-Medium (UIB + MQA) specifically so we can isolate what MQA adds. Same UIB foundation in both, same dataset, same training setup — the only architectural difference between the two models is the presence of Mobile MQA. Whatever accuracy/latency difference shows up between them should be attributable to MQA, not to anything else.

## What "Conv" and "Hybrid" mean in the model names

MobileNetV4 isn't one model, it's a family, and the name tells you which variant you're looking at:

- **MobileNetV4-Conv-{Small, Medium, Large}** — "Conv" means it's built *only* from convolution layers (UIB blocks, no attention anywhere).
- **MobileNetV4-Hybrid-{Medium, Large}** — these add Mobile MQA layers on top of the UIB blocks.

So the naming convention itself tells you upfront whether a given variant has attention or not — you don't have to dig into the code to check. Conv variants stay leaner and are aimed more at CPU/edge use; Hybrid variants trade a bit more compute for (hopefully) better accuracy. Testing whether that trade-off is actually worth it on CPU is exactly what our project measures.

## The two models we're using

**MobileNetV4-Conv-Small** — loaded via `timm`, pretrained ImageNet weights:

- Pure CNN, no attention — built entirely from UIB blocks as described above.
- **Corrected number:** the 3.77M figure I originally wrote down was measured *before* swapping the head (still the 1000-class ImageNet classifier). After `reset_classifier(num_classes=100)`, I re-ran the actual PyTorch param count and confirmed the real total is **2.621M parameters** — matches the ~2.6M estimate from working out the head-size difference by hand, so this number's solid now.
- MACs: **0.185G** at 224x224 input — measured after the head swap, this one was already correct.

**MobileNetV4-Hybrid-Medium** — also loaded via `timm`, pretrained ImageNet weights:

- UIB blocks + Mobile MQA attention layers.
- Same 224x224 input size as Conv-Small, so the data pipeline doesn't need to change between the two.
- Params/MACs analysis: `[to fill in once I run the same model_analysis steps on this one]`.

Both get the same classifier head treatment (`reset_classifier(num_classes=100)`) and go through the identical training script once Member 2 hands off the pipeline — same hyperparameters, same seed, for a fair comparison.

In the original paper, both variants are trained with distillation from a much bigger EfficientNet-L2 teacher — we're not doing that ourselves, just fine-tuning the pretrained weights directly, but good to know it's part of how the paper got its reported numbers.

## For the mid-eval slide

- Lead with the UIB block since it's the shared foundation across both our MobileNetV4 models — explains why the family is supposed to be efficient across different hardware, not just one target.
- Then introduce Mobile MQA as the *one* architectural difference between our two models — this is what makes the Conv-Small vs. Hybrid-Medium comparison meaningful, not just "two random models."
- Put up Conv-Small's confirmed numbers (**2.621M params, 0.185G MACs**) alongside Hybrid-Medium's once that model is analyzed post-mid-eval — side by side is the point, not just one model's numbers in isolation.
- MobileNetV2 sits alongside both as the older-generation baseline, so we can also say whether either MobileNetV4 variant actually beats a pre-UIB, pre-MQA architecture.
