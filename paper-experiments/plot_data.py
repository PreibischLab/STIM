import os

import numpy as np
import pandas as pd
import matplotlib.pyplot as plt


NAMES = ['automatic', 'SP', 'NK', 'DLP', 'MI', 'morpho', 'spacel']
LABELS =['STIM', 'Human #1', 'Human #2', 'Human #3', 'Human #4', 'Morpho', 'Spacel']
COLORS = ['tab:grey', 'tab:blue', 'tab:green', 'tab:red', 'tab:purple', 'tab:orange', 'tab:cyan']
MICRON_PER_PIXEL = 0.345
PAD = 5
NUM_GROUPS = len(NAMES)
script_dir = os.path.dirname(__file__)


# ---- Pairwise errors ----

data = []
colors = []

# load pairwise error data
# make sure that the data is present (i.e., execute compare.py before running this script)
for name1 in NAMES:
    for name2, color in zip(NAMES, COLORS):
        if name1 == name2:
            continue

        file_name = os.path.join(script_dir, f'compare-{name1}-{name2}.csv')
        df = pd.read_csv(file_name)

        colors.append(color)
        data.append(df['meanError'])

# plot pairwise error data as grouped boxplots
groups = np.arange(0, (NUM_GROUPS + PAD - 1) * NUM_GROUPS).reshape((NUM_GROUPS, -1))[:, :-PAD]
x = groups.flatten()
y = np.array(data).T * MICRON_PER_PIXEL
boxes = plt.boxplot(y, positions=x, patch_artist=True, medianprops=dict(color='black'))

# color boxes and outlier points
for patch, color in zip(boxes['boxes'], colors):
    patch.set(facecolor=color)
for flier, color in zip(boxes['fliers'], colors):
    flier.set(marker='o', markerfacecolor=color)

plt.ylabel('Pairwise error [µm]')
plt.xlim(np.min(groups) - PAD / 2, np.max(groups) + PAD / 2)
plt.grid(axis='y', linestyle='--', alpha=0.5)
plt.xticks(np.mean(groups, axis=1), LABELS, fontsize='small')
xtick_labels = plt.gca().get_xticklabels()
for label, color in zip(xtick_labels, COLORS):
    label.set_color(color)

plt.savefig('pairwise_error.pdf')

plt.ylim(0, 500)
plt.savefig('pairwise_error_zoom.pdf')
# plt.show()


# ---- Parameter scan ----

# load parameter scan data
file_name = os.path.join(script_dir, 'comparison-parameter-scan.csv')
df = pd.read_csv(file_name)

# plot parameter scan data in three groups of 6
plt.figure()

# Split the data into three groups
GROUP_SIZE = 6
NUM_GROUPS = len(df) // GROUP_SIZE
SPACING = 2  # space between groups
for i in range(NUM_GROUPS):
    start_idx = i * GROUP_SIZE
    end_idx = start_idx + GROUP_SIZE
    x_values = np.arange(start_idx, end_idx) + i * SPACING
    y_values = df['mean'][start_idx:end_idx] * MICRON_PER_PIXEL
    plt.plot(x_values, y_values, marker='o', label=f'Scale {df["scale"][start_idx]}')

# set xticks to be only the 'renderFactor'
xticks_labels = [f"{renderFactor}" for renderFactor in df['renderFactor']]
ticks = np.arange(len(df)) + (np.arange(len(df)) // GROUP_SIZE) * SPACING
plt.xticks(ticks=ticks, labels=xticks_labels, rotation=45, ha='right')

plt.xlabel('Render Factor')
plt.ylabel('Distance to Human #1 [µm]')
plt.legend(loc='upper left', fontsize='large', facecolor=(1, 1, 1, 1))
plt.grid(linestyle='--', alpha=0.5)
plt.tight_layout()
plt.savefig('parameter_scan.pdf')
# plt.show()
