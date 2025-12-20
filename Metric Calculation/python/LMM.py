import pandas as pd
import numpy as np
from statsmodels.formula.api import mixedlm
from sklearn.preprocessing import StandardScaler

# ===========================
# Load dataset
# ===========================
df = pd.read_csv("syra_final_sessions.csv")

# Drop missing required fields
df = df.dropna(subset=[
    "participant_id",
    "session_energy_weighted",
    "HR_mean_session",
    "RMSSD_session"
])

# ===========================
# Standardize continuous vars
# ===========================
# ===========================
# Standardize continuous vars
# ===========================
scaler = StandardScaler()

# --- CORRECTED LINES ---
# Use the "_weighted" columns from your CSV
df["energy_z"]  = scaler.fit_transform(df[["session_energy_weighted"]])
df["valence_z"] = scaler.fit_transform(df[["session_valence_weighted"]])
df["tempo_z"]   = scaler.fit_transform(df[["session_tempo_weighted"]])
# ---------------------

df["accel_z"]   = scaler.fit_transform(df[["accel_mean_session"]])

df["HR_mean_z"]   = scaler.fit_transform(df[["HR_mean_session"]])
df["RMSSD_z"]     = scaler.fit_transform(df[["RMSSD_session"]])
df["SDNN_z"]      = scaler.fit_transform(df[["SDNN_session"]])

# ===========================
# LMM #1: Physiology ~ Music
# ===========================
formula_hr = """
HR_mean_z ~ energy_z + tempo_z + valence_z + accel_z + session_duration_s
"""

model_hr = mixedlm(formula_hr, df, groups=df["participant_id"])
res_hr = model_hr.fit(reml=False)
print("\n=== LMM: Music Features → Heart Rate ===\n")
print(res_hr.summary())


# ===========================
# LMM #2: HRV ~ Music Features
# ===========================
formula_hrv = """
RMSSD_z ~ energy_z + tempo_z + valence_z + accel_z + session_duration_s
"""

model_hrv = mixedlm(formula_hrv, df, groups=df["participant_id"])
res_hrv = model_hrv.fit(reml=False)
print("\n=== LMM: Music Features → HRV ===\n")
print(res_hrv.summary())


# ===========================
# LMM #3: Movement ~ Music Features
# ===========================
formula_accel = """
accel_z ~ energy_z + tempo_z + valence_z + session_duration_s
"""

model_accel = mixedlm(formula_accel, df, groups=df["participant_id"])
res_accel = model_accel.fit(reml=False)
print("\n=== LMM: Music Features → Movement ===\n")
print(res_accel.summary())


# ===========================
# LMM #4: Post-Listening Arousal
# ===========================
formula_arousal = """
post_arousal ~ pre_arousal + energy_z + tempo_z + HR_mean_z + accel_z
"""

model_arousal = mixedlm(formula_arousal, df, groups=df["participant_id"])
res_arousal = model_arousal.fit(reml=False)
print("\n=== LMM: Music + Physiology → Arousal (Post-ESM) ===\n")
print(res_arousal.summary())


# ===========================
# (Optional) Trait Moderation
# ===========================
formula_trait = """
HR_mean_z ~ energy_z * bmrq_reward + accel_z
"""

model_trait = mixedlm(formula_trait, df, groups=df["participant_id"])
res_trait = model_trait.fit(reml=False)
print("\n=== LMM: Trait Moderation (BMRQ Reward) ===\n")
print(res_trait.summary())
