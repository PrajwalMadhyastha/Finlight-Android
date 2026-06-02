import argparse
import sys
import agent.loop as loop
import agent.reporter as reporter

def main():
    parser = argparse.ArgumentParser(description="Autonomous QA Agent Runner")
    parser.add_argument("--goal", type=str, required=True, help="The high level testing goal.")
    parser.add_argument("--max-steps", type=int, default=30, help="Maximum number of steps the agent can take.")
    
    args = parser.parse_args()
    
    print("="*50)
    print("🤖 Autonomous Android QA Agent")
    print("="*50)
    
    # Run the loop
    result = loop.run_agent_loop(goal=args.goal, max_steps=args.max_steps)
    
    # Generate the report
    reporter.generate_report(args.goal, result)
    
    # Exit with code 1 if it explicitly failed, helps CI know it failed
    if result.get("status", "").lower() == "fail":
        sys.exit(1)
    else:
        sys.exit(0)

if __name__ == "__main__":
    main()
