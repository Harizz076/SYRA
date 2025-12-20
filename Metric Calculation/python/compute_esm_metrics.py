#!/usr/bin/env python3
"""
Extract SYRA ESM metrics from pre.jsonl and post.jsonl
and output a single-row CSV for one session.

Output columns:
pre_esm_id, pre_arousal, pre_valence,
post_esm_id, post_arousal, post_valence,
liking_post, familiarity_post,
delta_arousal, delta_valence
"""

import json
import pandas as pd
import argparse


def read_single_jsonl(path):
    """Reads a JSONL file that contains exactly ONE record."""
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                return json.loads(line)
    raise ValueError(f"No valid entries found in {path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--pre", required=True, help="pre.jsonl file")
    parser.add_argument("--post", required=True, help="post.jsonl file")
    parser.add_argument("--output", default="esm_session_metrics.csv", help="Output CSV")
    args = parser.parse_args()

    # Ask for session ID
    session_id = input("Enter session_id: ")

    # -----------------------------------------
    # Load pre.jsonl
    # -----------------------------------------
    pre = read_single_jsonl(args.pre)

    pre_esm_id = f"{session_id}_PRE"
    pre_arousal = pre.get("energy")
    pre_valence = pre.get("pleasantness")

    # -----------------------------------------
    # Load post.jsonl
    # -----------------------------------------
    post = read_single_jsonl(args.post)

    post_esm_id = f"{session_id}_POST"
    post_arousal = post.get("energy")
    post_valence = post.get("pleasantness")
    liking_post = post.get("music_liking")
    familiarity_post = post.get("music_familiarity")

    # -----------------------------------------
    # Compute deltas
    # -----------------------------------------
    delta_arousal = None
    delta_valence = None

    if pre_arousal is not None and post_arousal is not None:
        delta_arousal = post_arousal - pre_arousal

    if pre_valence is not None and post_valence is not None:
        delta_valence = post_valence - pre_valence

    # -----------------------------------------
    # Build output dataframe
    # -----------------------------------------
    out = pd.DataFrame([{
        "pre_esm_id": pre_esm_id,
        "pre_arousal": pre_arousal,
        "pre_valence": pre_valence,
        "post_esm_id": post_esm_id,
        "post_arousal": post_arousal,
        "post_valence": post_valence,
        "liking_post": liking_post,
        "familiarity_post": familiarity_post,
        "delta_arousal": delta_arousal,
        "delta_valence": delta_valence
    }])

    out.to_csv(args.output, index=False)

    print(f"\nSaved → {args.output}")
    print(out)


if __name__ == "__main__":
    main()
#!/usr/bin/env python3
"""
Extract SYRA ESM metrics from pre.jsonl and post.jsonl
and output a single-row CSV for one session.

Output columns:
pre_esm_id, pre_arousal, pre_valence,
post_esm_id, post_arousal, post_valence,
liking_post, familiarity_post,
delta_arousal, delta_valence
"""

import json
import pandas as pd
import argparse


def read_single_jsonl(path):
    """Reads a JSONL file that contains exactly ONE record."""
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                return json.loads(line)
    raise ValueError(f"No valid entries found in {path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--pre", required=True, help="pre.jsonl file")
    parser.add_argument("--post", required=True, help="post.jsonl file")
    parser.add_argument("--output", default="esm_session_metrics.csv", help="Output CSV")
    args = parser.parse_args()

    # Ask for session ID
    session_id = input("Enter session_id: ")

    # -----------------------------------------
    # Load pre.jsonl
    # -----------------------------------------
    pre = read_single_jsonl(args.pre)

    pre_esm_id = f"{session_id}_PRE"
    pre_arousal = pre.get("energy")
    pre_valence = pre.get("pleasantness")

    # -----------------------------------------
    # Load post.jsonl
    # -----------------------------------------
    post = read_single_jsonl(args.post)

    post_esm_id = f"{session_id}_POST"
    post_arousal = post.get("energy")
    post_valence = post.get("pleasantness")
    liking_post = post.get("music_liking")
    familiarity_post = post.get("music_familiarity")

    # -----------------------------------------
    # Compute deltas
    # -----------------------------------------
    delta_arousal = None
    delta_valence = None

    if pre_arousal is not None and post_arousal is not None:
        delta_arousal = post_arousal - pre_arousal

    if pre_valence is not None and post_valence is not None:
        delta_valence = post_valence - pre_valence

    # -----------------------------------------
    # Build output dataframe
    # -----------------------------------------
    out = pd.DataFrame([{
        "pre_esm_id": pre_esm_id,
        "pre_arousal": pre_arousal,
        "pre_valence": pre_valence,
        "post_esm_id": post_esm_id,
        "post_arousal": post_arousal,
        "post_valence": post_valence,
        "liking_post": liking_post,
        "familiarity_post": familiarity_post,
        "delta_arousal": delta_arousal,
        "delta_valence": delta_valence
    }])

    out.to_csv(args.output, index=False)

    print(f"\nSaved → {args.output}")
    print(out)


if __name__ == "__main__":
    main()
