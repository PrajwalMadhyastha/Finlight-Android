import datetime
import os

def generate_report(goal: str, result: dict):
    timestamp = datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    filename = f"qa-report-{timestamp}.md"
    
    status = result.get("status", "unknown")
    summary = result.get("summary", "No summary provided.")
    history = result.get("history", [])
    
    report_lines = [
        f"# Autonomous QA Agent Report",
        f"**Date:** {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"**Goal:** {goal}",
        f"**Status:** {'✅ PASS' if status.lower() == 'pass' else '❌ FAIL'}",
        f"",
        f"## Summary",
        f"{summary}",
        f"",
        f"## Execution History"
    ]
    
    for item in history:
        step = item.get("step")
        reasoning = item.get("reasoning", "")
        action = item.get("action", "")
        args = item.get("args", {})
        
        report_lines.append(f"### Step {step}")
        report_lines.append(f"**Reasoning:** {reasoning}")
        report_lines.append(f"**Action:** `{action}`")
        report_lines.append(f"**Args:** `{args}`")
        report_lines.append("")
        
    content = "\n".join(report_lines)
    
    # Save the report in the root directory
    filepath = os.path.join(os.getcwd(), filename)
    with open(filepath, "w", encoding="utf-8") as f:
        f.write(content)
        
    print(f"\nReport successfully generated at: {filepath}")
    return filepath
