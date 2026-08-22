#!/usr/bin/env python3
"""
E2E Test Runner for MINI-AWS (MiniCloud).
Executes 4-Tier requirement-driven test suite with formatted results breakdown.

Usage:
    python run_e2e_tests.py [--tier 1|2|3|4|all] [--feature r1|r2|r3|r4|r5|all] [--url <url>] [-v]
"""
import argparse
import os
import sys
import time
from pathlib import Path
import pytest


def main():
    parser = argparse.ArgumentParser(description="MINI-AWS 4-Tier E2E Test Suite Runner")
    parser.add_argument("--tier", choices=["1", "2", "3", "4", "all"], default="all", help="Select tier to execute")
    parser.add_argument("--feature", choices=["r1", "r2", "r3", "r4", "r5", "all"], default="all", help="Select feature marker to execute")
    parser.add_argument("--url", default="http://localhost:8080", help="Target MiniCloud API base URL")
    parser.add_argument("-v", "--verbose", action="store_true", help="Verbose output")
    args = parser.parse_args()

    # Set base directory to e2e-tests
    e2e_dir = Path(__file__).resolve().parent
    os.chdir(e2e_dir)

    # Set environment variable for test execution
    if args.url:
        os.environ["MINICLOUD_API_URL"] = args.url

    pytest_args = ["-ra"]
    if args.verbose:
        pytest_args.append("-v")
    else:
        pytest_args.append("--tb=short")

    # Build target paths and markers
    markers = []
    if args.tier != "all":
        markers.append(f"tier{args.tier}")
    if args.feature != "all":
        markers.append(args.feature.lower())

    if markers:
        pytest_args.extend(["-m", " and ".join(markers)])

    print("=" * 78)
    print("  MINI-AWS (MiniCloud) — 4-Tier End-to-End Test Suite")
    print("=" * 78)
    print(f"  Target API URL : {os.environ.get('MINICLOUD_API_URL')}")
    print(f"  Selected Tier  : {args.tier.upper()}")
    print(f"  Feature Filter : {args.feature.upper()}")
    print(f"  Test Directory : {e2e_dir}")
    print("=" * 78)

    start_time = time.time()
    exit_code = pytest.main(pytest_args)
    duration = round(time.time() - start_time, 2)

    print("\n" + "=" * 78)
    if exit_code == 0:
        print(f"  >>> TEST SUITE PASSED (Duration: {duration}s) <<<")
    else:
        print(f"  >>> TEST SUITE FAILED (Exit Code: {exit_code}, Duration: {duration}s) <<<")
    print("=" * 78 + "\n")

    return int(exit_code)


if __name__ == "__main__":
    sys.exit(main())
