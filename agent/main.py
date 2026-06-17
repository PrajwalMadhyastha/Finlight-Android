import argparse
import sys
import agent.loop as loop
import agent.reporter as reporter

def main():
    parser = argparse.ArgumentParser(description="Autonomous QA Agent Runner")
    parser.add_argument("--goal", type=str, required=False, default="random", help="The high level testing goal.")
    parser.add_argument("--max-steps", type=int, default=100, help="Maximum number of steps the agent can take.")
    parser.add_argument("--api-level", type=str, default="Unknown", help="API Level of the emulator")
    parser.add_argument("--app-version", type=str, default="Unknown", help="App version being tested")
    
    args = parser.parse_args()
    
    print("="*50)
    print("🤖 Autonomous Android QA Agent")
    print("="*50)
    
    import random
    import agent.personas as personas
    import os
    
    memory_text = ""
    app_map_file = "app_map.json"
    if os.path.exists(app_map_file):
        import json
        with open(app_map_file, "r", encoding="utf-8") as f:
            try:
                app_map = json.load(f)
                memory_text = json.dumps(app_map, indent=2)
                print("Loaded previous agent memory (App Map).")
                print("--- APP MAP CONTENT ---")
                print(memory_text)
                print("-----------------------")
            except json.JSONDecodeError:
                print("Failed to parse app_map.json.")
    else:
        print("--- NO APP MAP FOUND ---")
                
    goal = args.goal
    if goal == "random":
        available_personas = [p for p in personas.PERSONAS if p not in memory_text]
        if not available_personas:
            print("All personas have been tested! Resetting the persona list.")
            available_personas = personas.PERSONAS
            
        goal = random.choice(available_personas)
        print(f"Random persona selected: {goal}")
    else:
        print(f"Goal selected: {goal}")
        
    import time
    start_time = time.time()
    
    # Run the loop
    result = loop.run_agent_loop(goal=goal, max_steps=args.max_steps, memory_text=memory_text)
    
    execution_time_seconds = int(time.time() - start_time)
    minutes = execution_time_seconds // 60
    seconds = execution_time_seconds % 60
    execution_time_str = f"{minutes} minutes {seconds} seconds" if minutes > 0 else f"{seconds} seconds"
    
    app_version = args.app_version
    if app_version == "Unknown":
        try:
            import re
            with open("version.properties", "r") as f:
                content = f.read()
                major = re.search(r"VERSION_MAJOR=(\d+)", content)
                minor = re.search(r"VERSION_MINOR=(\d+)", content)
                patch = re.search(r"VERSION_PATCH=(\d+)", content)
                if major and minor and patch:
                    app_version = f"{major.group(1)}.{minor.group(1)}.{patch.group(1)}"
        except Exception:
            pass

    metadata = {
        "App_Version": app_version,
        "API_Level": args.api_level,
        "Execution_Time": execution_time_str
    }
    
    # Generate the report
    reporter.generate_report(goal, result, metadata)
    
    # Exit with code 1 if it explicitly failed or was incomplete, helps CI know it failed
    if result.get("status", "").lower() != "pass":
        sys.exit(1)
    else:
        sys.exit(0)

if __name__ == "__main__":
    main()
