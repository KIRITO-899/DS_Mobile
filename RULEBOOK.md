# 📘 DS_Mobile — Project Rulebook

> **MobileNetV4 Image Classification on CIFAR-100**
> This document is the single source of truth for how every team member writes code, names things, commits changes, and collaborates. It lives on **every branch** and must be respected at all times.

---

## 📌 Table of Contents

1. [Branch Strategy](#1-branch-strategy)
2. [Commit Convention](#2-commit-convention)
3. [Code Style](#3-code-style)
4. [File & Folder Naming](#4-file--folder-naming)
5. [Docstrings & Comments](#5-docstrings--comments)
6. [Notebooks (.ipynb)](#6-notebooks-ipynb)
7. [Model & Experiment Tracking](#7-model--experiment-tracking)
8. [What NOT to Commit](#8-what-not-to-commit)
9. [Pull Request & Review Rules](#9-pull-request--review-rules)
10. [Environment & Dependencies](#10-environment--dependencies)
11. [Communication](#11-communication)

---

## 1. Branch Strategy

All member branches (`member-1`, `member-2`, `member-3`) have been **merged into `main`** and deleted. The project now follows a **feature-branch workflow**.

```
main                    <- stable, reviewed, merged code only
feature/<description>   <- short-lived branches for new work
fix/<description>       <- short-lived branches for bug fixes
```

| Rule | Detail |
|------|--------|
| **Never push directly to `main`** | All changes go through a Pull Request |
| **Branch from `main`** | Create a new branch for every task: `git checkout -b feature/add-training-pipeline` |
| **Keep branches short-lived** | Merge back into `main` within 1–3 days |
| **One feature, one branch** | Keep branches focused on a single task |
| **Delete merged branches** | Clean up feature branches after merging |

### Starting New Work
```bash
git checkout main
git pull origin main
git checkout -b feature/my-task    # or fix/my-bugfix
```

---

## 2. Commit Convention

Follow the **Conventional Commits** standard. Every commit message must be structured as:

```
<type>(<scope>): <short description>

[optional body — explain WHY, not WHAT]
[optional footer — issue refs, breaking changes]
```

### Allowed Types

| Type | When to Use |
|------|-------------|
| `feat` | New feature or model component |
| `fix` | Bug fix |
| `train` | Training run configuration or script changes |
| `data` | Dataset loading, transforms, augmentation |
| `exp` | Experiment, hyperparameter tuning |
| `refactor` | Code restructuring without behaviour change |
| `docs` | Documentation, comments, README |
| `chore` | Dependency updates, tooling, CI |
| `test` | Adding or fixing tests |

### Good Examples

```
feat(model): add MobileNetV4-Medium variant loader
fix(dataloader): correct mean/std normalization for CIFAR-100
train(scheduler): switch from StepLR to CosineAnnealingLR
exp(lr): compare 1e-3 vs 5e-4 on 10-epoch warm-up
docs(architecture): update architecture.md with layer counts
```

### Bad Examples

```
update stuff
fixed bug
WIP
asdfgh
```

### Rules
- Keep the subject line <= 72 characters
- Use **present tense, imperative mood** ("add X", not "added X")
- Reference issues when relevant: `Closes #12`
- Commit **logical units of work** — not a day's dump of code

---

## 3. Code Style

This project follows **PEP 8** with the additions below.

### Formatting
- **Indentation**: 4 spaces (no tabs)
- **Max line length**: 100 characters
- **Blank lines**: 2 between top-level definitions, 1 between methods

### Imports
Order imports in this exact sequence, separated by a blank line:
```python
# 1. Standard library
import os
import sys

# 2. Third-party
import torch
import timm

# 3. Internal / local
from mobilenetv4 import get_model
```

### Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Variables / functions | `snake_case` | `learning_rate`, `get_model()` |
| Classes | `PascalCase` | `TrainingLoop`, `DataConfig` |
| Constants | `UPPER_SNAKE_CASE` | `NUM_CLASSES = 100` |
| Files / modules | `snake_case` | `train_pipeline.py` |
| Private helpers | `_leading_underscore` | `_compute_loss()` |

### Type Hints
All **public functions must have type hints**:
```python
def get_model(num_classes: int = 100, pretrained: bool = True) -> torch.nn.Module:
    ...
```

### No Magic Numbers
Replace bare literals with named constants at the top of the file:
```python
# Good
NUM_CLASSES = 100
IMAGE_SIZE  = 224

# Bad
model.reset_classifier(100)
```

---

## 4. File & Folder Naming

```
DS_Mobile/
├── models/
│   ├── mobilenetv4.py          <- model architecture definition
│   ├── best_conv_small.pt      <- trained model checkpoint
│   └── mnv4_small_cifar100.pt  <- fine-tuned CIFAR-100 checkpoint
├── notebooks/
│   └── model_analysis.ipynb    <- exploratory .ipynb files ONLY
├── predict.py                  <- CIFAR-100 inference script
├── train.py                    <- training entry point (to be added)
├── evaluate.py                 <- evaluation scripts (to be added)
├── config.py                   <- all hyperparameters & paths (to be added)
├── architecture_notes.md       <- model architecture documentation
├── requirements.txt
└── RULEBOOK.md                 <- this file
```

| Rule | Detail |
|------|--------|
| All Python files → `snake_case.py` | No spaces, no hyphens |
| Notebooks live in `notebooks/` | Never import from `.ipynb` files |
| No date-stamped files | Not `train_v2_final_Aug24.py` — use git for versioning |
| One responsibility per file | `train.py` trains; `evaluate.py` evaluates |

---

## 5. Docstrings & Comments

Every **public function and class** requires a Google-style docstring:

```python
def train_one_epoch(
    model: torch.nn.Module,
    loader: DataLoader,
    optimizer: Optimizer,
    criterion: nn.Module,
    device: torch.device,
) -> dict[str, float]:
    """Run one full training epoch.

    Args:
        model: The neural network to train.
        loader: DataLoader yielding (images, labels) batches.
        optimizer: Optimizer instance (e.g., AdamW).
        criterion: Loss function (e.g., CrossEntropyLoss).
        device: Device to move tensors to.

    Returns:
        A dict with keys ``loss`` and ``accuracy`` (float, epoch averages).
    """
```

### Inline Comments
- Explain **why**, not **what**
- Use full sentences starting with a capital letter
- Place above the relevant line, not beside it

```python
# Freeze backbone to fine-tune only the classifier head in early epochs.
for param in model.parameters():
    param.requires_grad = False
model.get_classifier().requires_grad_(True)
```

---

## 6. Notebooks (.ipynb)

Notebooks are for **exploration and presentation only** — never for production logic.

| Rule | Detail |
|------|--------|
| Store in `notebooks/` | Never in the project root |
| Clear outputs before committing | Kernel → Restart & Clear Output |
| Numbered naming | `01_data_exploration.ipynb`, `02_loss_curves.ipynb` |
| No `!pip install` in cells | Use `requirements.txt` instead |
| No hardcoded paths | Use `pathlib.Path` or `os.path` relative to project root |
| Import from `.py` modules | Keep reusable code in `.py` files, not notebook cells |

---

## 7. Model & Experiment Tracking

### Checkpoint Naming Convention
```
checkpoints/
└── mobilenetv4_small_cifar100_e{epoch:03d}_acc{val_acc:.4f}.pth
```
Example: `mobilenetv4_small_cifar100_e050_acc0.7823.pth`

### Log Every Run
Every training run **must** log the following:
- Model name and variant
- Number of parameters
- Dataset and number of classes
- Hyperparameters (LR, batch size, optimizer, scheduler)
- Best validation accuracy and the epoch it was achieved
- Total training time

### Config-Driven Training
All hyperparameters must live in **`config.py`** — not scattered across scripts:

```python
# config.py
NUM_CLASSES   = 100
BATCH_SIZE    = 128
LEARNING_RATE = 1e-3
EPOCHS        = 50
IMAGE_SIZE    = 224
PRETRAINED    = True
```

---

## 8. What NOT to Commit

These are **hard rules** — violations may cause the PR to be rejected.

| Item | Reason |
|------|--------|
| `*.pth`, `*.pt`, `*.ckpt` | Model weights are too large; use shared storage |
| `data/`, `datasets/` | Datasets are not version-controlled |
| `*.csv`, `*.pkl`, `*.npy` | Large data artefacts |
| `.env`, `secrets.yaml` | Credentials and API keys |
| `venv/`, `.venv/`, `hy/` | Virtual environments |
| `__pycache__/` | Python bytecode |
| `.ipynb_checkpoints/` | Jupyter auto-saves |
| `wandb/`, `mlruns/` | Experiment run artefacts |
| Notebook outputs | Clear before committing |

> WARNING: The `.gitignore` already covers most of these. **Never `git add -A` blindly** — always `git status` first.

---

## 9. Pull Request & Review Rules

### Opening a PR
1. Your branch must be up-to-date with `main` before opening a PR
2. PR title follows the same Conventional Commits format: `feat(train): add cosine LR scheduler`
3. Fill in the PR description:
   - **What changed** and **why**
   - **How to test it**
   - Link to relevant experiment results or notebook

### Review Checklist (for reviewers)
- [ ] Code follows style rules (naming, type hints, docstrings)
- [ ] No disallowed files are included
- [ ] No hardcoded magic numbers or paths
- [ ] Logic is correct and not duplicating existing functionality
- [ ] Notebook outputs are cleared (if applicable)

### Merging Rules
- **Minimum 1 approval** required before merging to `main`
- Use **Squash and Merge** for feature branches to keep history clean
- The author merges their own PR after approval — don't merge others' work without asking

---

## 10. Environment & Dependencies

### Setting Up
```bash
# Clone repo
git clone <repo-url>
cd DS_Mobile

# Create virtual environment (outside the repo)
python -m venv ../venv_ds_mobile
source ../venv_ds_mobile/bin/activate    # Linux/Mac
..\venv_ds_mobile\Scripts\activate       # Windows

# Install dependencies
pip install -r requirements.txt
```

### Adding a Dependency
1. Install it: `pip install <package>`
2. **Immediately** add it to `requirements.txt` with a pinned or minimum version:
   ```
   timm>=0.9.0
   torch>=2.0.0
   torchvision>=0.15.0
   ```
3. Commit the change to `requirements.txt` alongside the code that uses it

> WARNING: **Never commit a virtual environment folder** (`venv/`, `hy/`, etc.)

---

## 11. Communication

| Situation | Action |
|-----------|--------|
| Starting work on a task | Post in the team chat with what you're doing |
| Blocked for > 30 min | Ask for help — don't silently spin |
| You break `main` | Notify the team immediately and open a fix PR |
| Changing a shared interface (e.g., `get_model()` signature) | Discuss before changing — other members depend on it |
| Experiment results are ready | Share a summary (accuracy table, loss curves) in the chat |

---

## Quick Reference Card

```
Branch         -> feature/<name> or fix/<name> off main
Commit msg     -> feat/fix/train/exp(scope): imperative description
Code style     -> PEP 8, 100 char limit, type hints on public fns
Docstrings     -> Google-style on every public function/class
Constants      -> UPPER_SNAKE_CASE in config.py, never magic numbers
Notebooks      -> notebooks/ folder, cleared outputs, numbered names
Models         -> models/ folder for .py definitions and .pt checkpoints
No commits     -> weights, datasets, .env, __pycache__, venv
PR             -> up-to-date with main, 1 approval, squash merge
Dependencies   -> pin in requirements.txt immediately
```

---

*Last updated: 2026-10-21 · DS_Mobile Team*
