import time
import agent.adb_utils as adb_utils
import agent.ui_parser as ui_parser
import agent.brain as brain

def run_agent_loop(goal: str, max_steps: int = 50, memory_text: str = ""):
    model = brain.setup_gemini()
    history = []
    
    print(f"Starting QA Agent with Goal: {goal}")
    print("Launching app...")
    adb_utils.launch_app()
    
    # Phase 3: The Chaos Start
    adb_utils.random_monkey_events(count=5)
    
    status = "incomplete"
    summary = ""
    
    for step in range(max_steps):
        print(f"\n--- Step {step + 1} ---")
        time.sleep(2) # Wait for UI to settle
        
        ui_state_json = ui_parser.get_ui_json()
        if "error" in ui_state_json.lower() and "failed to get ui" in ui_state_json.lower():
             print("Error getting UI state. Retrying...")
             time.sleep(2)
             ui_state_json = ui_parser.get_ui_json()
             
        print("Thinking...")
        action_obj = brain.get_next_action(model, goal, ui_state_json, history, memory_text)
        
        reasoning = action_obj.get("reasoning", "No reasoning provided.")
        action = action_obj.get("action", "error")
        args = action_obj.get("args", {})
        
        print(f"Reasoning: {reasoning}")
        print(f"Action: {action}")
        print(f"Args: {args}")
        
        # Record history (without massive UI dump)
        history.append({
            "step": step + 1,
            "reasoning": reasoning,
            "action": action,
            "args": args
        })
        
        # Execute Action
        if action == "tap":
            adb_utils.tap(args.get("x", 0), args.get("y", 0))
        elif action == "long_press":
            adb_utils.long_press(args.get("x", 0), args.get("y", 0))
        elif action == "type":
            adb_utils.type_text(args.get("text", ""))
        elif action == "clear_text":
            adb_utils.clear_text()
        elif action == "swipe":
            adb_utils.swipe(args.get("x1", 0), args.get("y1", 0), args.get("x2", 0), args.get("y2", 0))
        elif action == "back":
            adb_utils.press_back()
        elif action == "home":
            adb_utils.press_home()
        elif action == "recent_apps":
            adb_utils.recent_apps()
        elif action == "background_app":
            adb_utils.background_app(args.get("seconds", 3))
        elif action == "trigger_process_death":
            adb_utils.trigger_process_death()
        elif action == "toggle_dark_mode":
            adb_utils.toggle_dark_mode()
        elif action == "sleep":
            time.sleep(args.get("seconds", 2))
        elif action == "checkpoint":
            checkpoint_summary = args.get("summary", "Reached a checkpoint.")
            print(f"\n[CHECKPOINT REACHED]: {checkpoint_summary}")
            # Wipe history and replace with checkpoint summary to compress context
            history = [{
                "step": step + 1,
                "action": "checkpoint",
                "summary": checkpoint_summary
            }]
        elif action == "finish":
            status = args.get("status", "unknown")
            summary = args.get("summary", "No summary provided.")
            print(f"\nGoal Finished! Status: {status}")
            break
        elif action == "error":
            print("LLM Error, attempting to continue...")
        else:
            print(f"Unknown action {action}")
            
    if status == "incomplete":
        print(f"\nReached max steps ({max_steps}) without finishing.")
        summary = "Agent did not complete the goal within the maximum allowed steps."
        
    return {
        "status": status,
        "summary": summary,
        "history": history
    }
