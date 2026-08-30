# MobileNetV4 Architecture Notes

These are my notes on how MobileNetV4-Small is put together, mainly so I actually understand what I'm loading via `timm` and not just treating it as a black box. Writing this up for the mid-eval too.

## Why MobileNetV4 is different — the UIB block

The main thing MobileNetV4 does differently from V2/V3 is that it doesn't use one fixed block design everywhere. Instead it has this one flexible block called the **Universal Inverted Bottleneck (UIB)**, and depending on which parts of it are switched on or off, it can basically turn into a few different block types that older architectures used separately.

A UIB block is built from these pieces, in order:

`Depthwise Start -> Pointwise Expansion -> Depthwise Middle -> SE -> Pointwise Projection -> Depthwise End`

Any of the depthwise steps can just be an identity (i.e. skipped), and that's what changes the block's "personality":

- If **both** `dw_start` and `dw_mid` are active, you get what the paper calls **ExtraDW**. Basically two rounds of cheap spatial filtering, which helps the network see a wider area without spending much compute.
- If only `dw_mid` is active and `dw_start` is skipped, that's just the regular **Inverted Bottleneck** block you'd recognize from MobileNetV2/V3.
- If `dw_start` is active but `dw_mid` is skipped, that's closer to a **ConvNeXt-style** block — spatial mixing happens before the channels get expanded, instead of after.

I actually saw this directly when I printed out the model in my notebook — some blocks show `dw_start` as `Identity()` and others show `dw_mid` as `Identity()`, which lines up exactly with what I just described. So this isn't just a diagram in the paper, it's really how the model is built layer by layer.

The reason this matters (from what I understand): letting a NAS search pick and mix these variants per-layer means the network can put "expensive" blocks only where they're worth it and cheap ones everywhere else, instead of using the same block shape throughout like older models did.

## Mobile MQA — not actually in our model

Worth flagging clearly: Mobile MQA (their optimized attention mechanism) only shows up in the **hybrid** MobileNetV4 variants. We're using **MobileNetV4-Conv-Small**, which is pure convolution, no attention at all. So if this comes up at the mid-eval, I should be upfront that our model doesn't use MQA — it's a UIB-only architecture. Don't want to imply we're using something we're not.

## Wait, what does "Conv" actually mean in the name?

Quick thing I had to look up myself: MobileNetV4 isn't just one model, it comes in a few versions, and the name tells you which one:

- **MobileNetV4-Conv-{Small, Medium, Large}** — "Conv" means it's built *only* from convolution layers (the UIB blocks described above). No attention anywhere. This is the one we're using.
- **MobileNetV4-Hybrid-{Medium, Large}** — these mix in Mobile MQA (attention) layers on top of the UIB blocks, usually in the later stages of the network where the feature maps have already shrunk down, so attention is cheaper to run there.

So basically the model's own name is telling us upfront that it has no attention mechanism — that's not something I had to dig into the code to figure out, it's built into the naming convention. The Hybrid versions trade a bit of extra compute for a small accuracy bump; the Conv versions stay leaner and are aimed more at CPU/edge use, which fits what our project is actually testing (CPU latency comparisons).

## The specific model we're using

MobileNetV4-Conv-Small, loaded through `timm` with pretrained ImageNet weights:

- Pure CNN, no attention — built entirely from UIB blocks as described above.
- Meant for edge devices / CPU inference, which is basically the whole point of our project (comparing CPU latency across models).
- In the original paper they trained this using distillation from a much bigger EfficientNet-L2 teacher — we're not doing that ourselves, just fine-tuning the pretrained weights directly, but good to know it's part of how the paper got its reported numbers.
- After I swapped the classifier head for our 100 CIFAR-100 classes: **3.77M parameters**, **0.185G MACs** at 224x224 input. These numbers came straight out of `model_analysis.ipynb`.

## For the mid-eval slide

- Lead with the UIB block since that's the actual novel contribution — good way to explain why MobileNetV4 is supposed to be efficient across different hardware, not just one target.
- Mention early that we're not using Mobile MQA, so nobody's confused later.
- Put up the 3.77M params / 0.185G MACs numbers as our starting reference point — once Member 2 finishes training and Member 3 benchmarks V2/V3-Small against this, these are the numbers everything else gets compared to.