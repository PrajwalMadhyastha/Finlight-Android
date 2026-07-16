import datetime
import os
import json
import agent.brain as brain

def generate_report(goal: str, result: dict, metadata: dict = None):
    timestamp = datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    filename = f"qa-report-{timestamp}.md"
    
    status = result.get("status", "unknown")
    summary = result.get("summary", "No summary provided.")
    history = result.get("history", [])
    
    report_lines = [
        f"# Autonomous QA Agent Report",
        f"**Date:** {datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
        f"**Goal:** {goal}",
        f"**Status:** {'PASS' if status.lower() == 'pass' else 'FAIL'}",
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
    
    app_map = {"Metadata": {"App_Version": "Unknown", "API_Level": "Unknown", "Execution_Time": "0 seconds"}, "Screens": {}, "Known_Bugs": []}
    if os.path.exists(app_map_file):
        with open(app_map_file, "r", encoding="utf-8") as f:
            try:
                app_map = json.load(f)
            except json.JSONDecodeError:
                pass
                
    if metadata:
        if "Metadata" not in app_map:
            app_map["Metadata"] = {}
        app_map["Metadata"].update(metadata)
        
    # Remove deprecated keys that might be lingering from older app maps
    app_map.pop("Features_Tested", None)
    app_map.pop("Screens_Visited", None)
    if "Metadata" in app_map and "Execution_Time_Seconds" in app_map["Metadata"]:
        app_map["Metadata"].pop("Execution_Time_Seconds", None)
                
    client = brain.setup_gemini()
    print("Summarizing run to update App Map...")
    diff = brain.summarize_run_to_app_map(client, app_map, goal, summary, history)
    
    # Merge diff
    if "Screens" in diff and isinstance(diff["Screens"], dict):
        if "Screens" not in app_map:
            app_map["Screens"] = {}
        for screen_name, screen_data in diff["Screens"].items():
            if screen_name not in app_map["Screens"]:
                app_map["Screens"][screen_name] = {"Features_Tested": []}
            
            if "Features_Tested" in screen_data:
                existing_features = app_map["Screens"][screen_name].setdefault("Features_Tested", [])
                for feature in screen_data["Features_Tested"]:
                    # Trust the LLM's semantic deduplication
                    existing_features.append(feature)
                        
    if "New_Bugs" in diff and isinstance(diff["New_Bugs"], list):
        new_bugs_added = False
        for b in diff["New_Bugs"]:
            # Trust the LLM's semantic deduplication
            app_map.setdefault("Known_Bugs", []).append(b)
            new_bugs_added = True
        
        if new_bugs_added:
            with open("new_bugs_found.txt", "w") as f:
                f.write("true")
                
    if "Resolved_Bugs" in diff and isinstance(diff["Resolved_Bugs"], list):
        for rb in diff["Resolved_Bugs"]:
            for existing_bug in app_map.setdefault("Known_Bugs", []):
                rb_str = str(rb).lower()
                desc = existing_bug.get("Description", "").lower()
                bug_id = str(existing_bug.get("Id", "")).lower()
                if existing_bug.get("Status") == "Open" and (rb_str in desc or desc in rb_str or rb_str in bug_id):
                    existing_bug["Status"] = "Resolved"
                    print(f"Marked bug as Resolved: {existing_bug.get('Description')}")
                    
    with open(app_map_file, "w", encoding="utf-8") as f:
        json.dump(app_map, f, indent=2)
        
    print(f"Memory successfully updated at: {app_map_file}")
        
    return filepath
