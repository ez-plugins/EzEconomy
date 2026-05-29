#!/usr/bin/env python3
import argparse
import csv
import json
import os
from pathlib import Path

OPS = ["deposit", "withdraw", "balance_has"]
BANK_OPS = ["bank_deposit", "bank_withdraw", "bank_balance_has"]


def load_results(root: Path):
    rows = []
    for path in root.rglob("result.json"):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            continue
        rows.append(data)
    return rows


def load_bank_results(root: Path):
    rows = []
    for path in root.rglob("result-bank.json"):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            continue
        rows.append(data)
    return rows


def metric(data, op, field):
    metrics = data.get("metrics", {})
    op_data = metrics.get(op, {}) if isinstance(metrics, dict) else {}
    return op_data.get(field)


def fmt_ms(value):
    if value is None:
        return "N/A"
    try:
        return f"{float(value):.6f}"
    except Exception:
        return "N/A"

def fmt_mib(value):
    if value is None:
        return "N/A"
    try:
        return f"{float(value):.2f}"
    except Exception:
        return "N/A"


def key(d):
    return (d.get("plugin", "unknown"), d.get("storage", "unknown"), d.get("redis", "unknown"))


def make_summary(rows):
    baseline = {}
    for r in rows:
        if r.get("status") == "ok" and r.get("plugin") == "ezeconomy" and r.get("redis") == "off":
            baseline[(r.get("storage"),)] = r

    lines = []
    lines.append("| Plugin | Version | Storage | Redis | Status | Deposit avg (ms) | Withdraw avg (ms) | Balance/Has avg (ms) | RAM avg (MiB) | RAM peak (MiB) | ? vs EzEconomy baseline |")
    lines.append("|---|---|---|---|---|---:|---:|---:|---:|---:|---:|")

    for r in sorted(rows, key=key):
        status = r.get("status", "unknown")
        dep = metric(r, "deposit", "averageMs")
        wdr = metric(r, "withdraw", "averageMs")
        bal = metric(r, "balance_has", "averageMs")
        ram_avg = metric(r, "balance_has", "avgUsedRamMiB")
        ram_peak = metric(r, "balance_has", "peakUsedRamMiB")

        delta = "N/A"
        if status == "ok":
            base = baseline.get((r.get("storage"),))
            if base is not None:
                base_dep = metric(base, "deposit", "averageMs")
                if base_dep not in (None, 0):
                    try:
                        ratio = float(dep) / float(base_dep)
                        delta = f"{ratio:.2f}x deposit"
                    except Exception:
                        delta = "N/A"

        if status == "skipped":
            reason = r.get("reason", "unsupported")
            status = f"skipped ({reason})"
        elif status == "failed":
            reason = r.get("reason", "error")
            status = f"failed ({reason})"

        lines.append(
            f"| {r.get('plugin','unknown')} | {r.get('pluginVersion','unknown')} | {r.get('storage','unknown')} | {r.get('redis','unknown')} | {status} | {fmt_ms(dep)} | {fmt_ms(wdr)} | {fmt_ms(bal)} | {fmt_mib(ram_avg)} | {fmt_mib(ram_peak)} | {delta} |"
        )

    return "\n".join(lines) + "\n"


def make_bank_summary(rows):
    lines = []
    lines.append("| Plugin | Version | Storage | Redis | Status | BankDeposit avg (ms) | BankWithdraw avg (ms) | BankBalance/Has avg (ms) | RAM avg (MiB) | RAM peak (MiB) |")
    lines.append("|---|---|---|---|---|---:|---:|---:|---:|---:|")

    for r in sorted(rows, key=key):
        status = r.get("status", "unknown")
        dep = metric(r, "bank_deposit", "averageMs")
        wdr = metric(r, "bank_withdraw", "averageMs")
        bal = metric(r, "bank_balance_has", "averageMs")
        ram_avg = metric(r, "bank_balance_has", "avgUsedRamMiB")
        ram_peak = metric(r, "bank_balance_has", "peakUsedRamMiB")

        if status == "skipped":
            reason = r.get("reason", "unsupported")
            status = f"skipped ({reason})"
        elif status == "failed":
            reason = r.get("reason", "error")
            status = f"failed ({reason})"

        lines.append(
            f"| {r.get('plugin','unknown')} | {r.get('pluginVersion','unknown')} | {r.get('storage','unknown')} | {r.get('redis','unknown')} | {status} | {fmt_ms(dep)} | {fmt_ms(wdr)} | {fmt_ms(bal)} | {fmt_mib(ram_avg)} | {fmt_mib(ram_peak)} |"
        )

    return "\n".join(lines) + "\n"


def write_csv(rows, out_path: Path):
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow([
            "plugin", "pluginVersion", "storage", "redis", "status", "reason",
            "deposit_avg_ms", "withdraw_avg_ms", "balance_has_avg_ms",
            "deposit_p95_ms", "withdraw_p95_ms", "balance_has_p95_ms",
            "deposit_avg_ram_mib", "withdraw_avg_ram_mib", "balance_has_avg_ram_mib",
            "deposit_peak_ram_mib", "withdraw_peak_ram_mib", "balance_has_peak_ram_mib"
        ])
        for r in sorted(rows, key=key):
            writer.writerow([
                r.get("plugin", "unknown"),
                r.get("pluginVersion", "unknown"),
                r.get("storage", "unknown"),
                r.get("redis", "unknown"),
                r.get("status", "unknown"),
                r.get("reason", ""),
                metric(r, "deposit", "averageMs"),
                metric(r, "withdraw", "averageMs"),
                metric(r, "balance_has", "averageMs"),
                metric(r, "deposit", "p95Ms"),
                metric(r, "withdraw", "p95Ms"),
                metric(r, "balance_has", "p95Ms"),
                metric(r, "deposit", "avgUsedRamMiB"),
                metric(r, "withdraw", "avgUsedRamMiB"),
                metric(r, "balance_has", "avgUsedRamMiB"),
                metric(r, "deposit", "peakUsedRamMiB"),
                metric(r, "withdraw", "peakUsedRamMiB"),
                metric(r, "balance_has", "peakUsedRamMiB"),
            ])


def write_bank_csv(rows, out_path: Path):
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with out_path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow([
            "plugin", "pluginVersion", "storage", "redis", "status", "reason",
            "bank_deposit_avg_ms", "bank_withdraw_avg_ms", "bank_balance_has_avg_ms",
            "bank_deposit_p95_ms", "bank_withdraw_p95_ms", "bank_balance_has_p95_ms",
            "bank_deposit_avg_ram_mib", "bank_withdraw_avg_ram_mib", "bank_balance_has_avg_ram_mib",
            "bank_deposit_peak_ram_mib", "bank_withdraw_peak_ram_mib", "bank_balance_has_peak_ram_mib"
        ])
        for r in sorted(rows, key=key):
            writer.writerow([
                r.get("plugin", "unknown"),
                r.get("pluginVersion", "unknown"),
                r.get("storage", "unknown"),
                r.get("redis", "unknown"),
                r.get("status", "unknown"),
                r.get("reason", ""),
                metric(r, "bank_deposit", "averageMs"),
                metric(r, "bank_withdraw", "averageMs"),
                metric(r, "bank_balance_has", "averageMs"),
                metric(r, "bank_deposit", "p95Ms"),
                metric(r, "bank_withdraw", "p95Ms"),
                metric(r, "bank_balance_has", "p95Ms"),
                metric(r, "bank_deposit", "avgUsedRamMiB"),
                metric(r, "bank_withdraw", "avgUsedRamMiB"),
                metric(r, "bank_balance_has", "avgUsedRamMiB"),
                metric(r, "bank_deposit", "peakUsedRamMiB"),
                metric(r, "bank_withdraw", "peakUsedRamMiB"),
                metric(r, "bank_balance_has", "peakUsedRamMiB"),
            ])


def validate(rows):
    for r in rows:
        status = r.get("status")
        if status not in {"ok", "skipped", "failed"}:
            raise ValueError(f"Invalid status in result: {status}")
        if "plugin" not in r or "storage" not in r or "redis" not in r:
            raise ValueError("Missing required metadata fields")
        if status == "ok":
            for op in OPS:
                avg = metric(r, op, "averageMs")
                if avg is None:
                    raise ValueError(f"Missing averageMs for op={op}")
                if float(avg) <= 0:
                    raise ValueError(f"averageMs must be > 0 for op={op}")
                avg_ram = metric(r, op, "avgUsedRamBytes")
                peak_ram = metric(r, op, "peakUsedRamBytes")
                if avg_ram is None or peak_ram is None:
                    raise ValueError(f"Missing RAM metrics for op={op}")


def main():
    parser = argparse.ArgumentParser(description="Aggregate benchmark artifacts.")
    parser.add_argument("--input", required=True)
    parser.add_argument("--out-json", required=True)
    parser.add_argument("--out-csv", required=True)
    parser.add_argument("--out-md", required=True)
    parser.add_argument("--out-bank-json")
    parser.add_argument("--out-bank-csv")
    args = parser.parse_args()

    root = Path(args.input)
    rows = load_results(root)
    if not rows:
        raise SystemExit("No benchmark result.json files found")

    validate(rows)

    out_json = Path(args.out_json)
    out_json.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(json.dumps(rows, indent=2), encoding="utf-8")

    write_csv(rows, Path(args.out_csv))

    summary = make_summary(rows)

    bank_rows = load_bank_results(root)
    bank_section = ""
    if bank_rows:
        bank_section = "\n## Bank Operation Benchmarks\n\n" + make_bank_summary(bank_rows) + "\n"
        if args.out_bank_json:
            out_bank_json = Path(args.out_bank_json)
            out_bank_json.parent.mkdir(parents=True, exist_ok=True)
            out_bank_json.write_text(json.dumps(bank_rows, indent=2), encoding="utf-8")
        if args.out_bank_csv:
            write_bank_csv(bank_rows, Path(args.out_bank_csv))

    out_md = Path(args.out_md)
    out_md.parent.mkdir(parents=True, exist_ok=True)
    out_md.write_text(summary + bank_section, encoding="utf-8")

    github_step_summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if github_step_summary:
        with open(github_step_summary, "a", encoding="utf-8") as f:
            f.write("\n## Economy Benchmark Summary\n\n")
            f.write(summary)
            if bank_section:
                f.write(bank_section)


if __name__ == "__main__":
    main()
