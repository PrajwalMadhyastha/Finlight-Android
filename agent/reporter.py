import datetime
import os
import json
import agent.brain as brain

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
    
    # Update Memory File
    app_map_file = os.path.join(os.getcwd(), "app_map.json")
    
    app_map = {"Screens_Visited": [], "Features_Tested": [], "Known_Bugs": []}
    if os.path.exists(app_map_file):
        with open(app_map_file, "r", encoding="utf-8") as f:
            try:
                app_map = json.load(f)
            except json.JSONDecodeError:
                pass
                
    client = brain.setup_gemini()
    print("Summarizing run to update App Map...")
    diff = brain.summarize_run_to_app_map(client, app_map, goal, summary, history)
    
    # Merge diff
    if "New_Screens" in diff and isinstance(diff["New_Screens"], list):
        for s in diff["New_Screens"]:
            if s not in app_map.setdefault("Screens_Visited", []):
                app_map["Screens_Visited"].append(s)
                
    if "New_Features" in diff and isinstance(diff["New_Features"], list):
        for f in diff["New_Features"]:
            if f not in app_map.setdefault("Features_Tested", []):
                app_map["Features_Tested"].append(f)
                
    if "New_Bugs" in diff and isinstance(diff["New_Bugs"], list):
        for b in diff["New_Bugs"]:
            # Ensure it's not a duplicate description
            exists = any(existing_bug.get("Description") == b.get("Description") for existing_bug in app_map.setdefault("Known_Bugs", []))
            if not exists:
                app_map["Known_Bugs"].append(b)
                
    if "Resolved_Bugs" in diff and isinstance(diff["Resolved_Bugs"], list):
        for rb in diff["Resolved_Bugs"]:
            # Try to match the description and mark as resolved
            for existing_bug in app_map.setdefault("Known_Bugs", []):
                if existing_bug.get("Status") == "Open" and (rb.lower() in existing_bug.get("Description", "").lower() or existing_bug.get("Description", "").lower() in rb.lower()):
                    existing_bug["Status"] = "Resolved"
                    print(f"Marked bug as Resolved: {existing_bug.get('Description')}")
                    
    with open(app_map_file, "w", encoding="utf-8") as f:
        json.dump(app_map, f, indent=2)
        
    print(f"Memory successfully updated at: {app_map_file}")
        
    return filepath
