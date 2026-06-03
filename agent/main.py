import argparse
import sys
import agent.loop as loop
import agent.reporter as reporter

def main():
    parser = argparse.ArgumentParser(description="Autonomous QA Agent Runner")
    parser.add_argument("--goal", type=str, required=False, default="random", help="The high level testing goal.")
    parser.add_argument("--max-steps", type=int, default=50, help="Maximum number of steps the agent can take.")
    
    args = parser.parse_args()
    
    print("="*50)
    print("🤖 Autonomous Android QA Agent")
    print("="*50)
    
    import random
    import agent.personas as personas
    
    goal = args.goal
    if goal == "random":
        goal = random.choice(personas.PERSONAS)
        print(f"Random persona selected: {goal}")
    else:
        print(f"Goal selected: {goal}")
        
    import os
    memory_text = ""
    if os.path.exists("agent_memory.txt"):
        with open("agent_memory.txt", "r", encoding="utf-8") as f:
            memory_text = f.read()
            print("Loaded previous agent memory.")
        
    # Run the loop
    result = loop.run_agent_loop(goal=goal, max_steps=args.max_steps, memory_text=memory_text)
    
    # Generate the report
    reporter.generate_report(goal, result)
    
    # Exit with code 1 if it explicitly failed, helps CI know it failed
    if result.get("status", "").lower() == "fail":
        sys.exit(1)
    else:
        sys.exit(0)

if __name__ == "__main__":
    main()
