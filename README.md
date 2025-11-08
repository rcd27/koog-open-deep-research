> 🚧 **Project Status: Work in Progress**  
>  
> This project is actively under development — expect frequent updates, refactors, and new experimental features.  
> Contributions, bug reports, and feedback are always welcome! 🧠💻  
>  
> _Stay tuned and star ⭐ the repo to follow the progress!_


#### Problems:
* "Cold" start allways forces LLM to execute once with NO messages but Message.System(which is defined in AIAgent 
  Config)
* Evaluations pretend to mock message history; however, standalone strategies are not mocked from tool calls. 
* ResearchSupervisor is not used
* Limits should be built in to Researcher

#### Prerequisites

- Tavily-MCP(currently with local MCP Server):
```shell
npx -y tavily-mcp@latest
```
