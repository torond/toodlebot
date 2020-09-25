# Workflow for Software Projects

## Graph Legend

- Circles: Start and Stop of compelete graph
- Boxes: Do
- Up Trapezoid: Start of graph section
- Down Trapezoid: End of graph section
- Flag: Topic of Discussion, requires documentation on done
- Rhombus: Questions/Decision

```mermaid
graph TD
  Start((Start)) --> A

  subgraph Fundamental Decisions
    A[/Decide Fundamentals\] --> A.1>Project Goal] --> A.EOG
    A --> A.2>Project Name] --> A.EOG
    A --> A.3>Technology] --> A.EOG
    A --> A.4>Team Name] --> A.EOG
    A --> A.5>License Agreement] --> A.EOG
    A --> A.6>Todo Collection] --> A.EOG
  end

  A.EOG[\Fundamentals <br> done/] --> B

  subgraph Repo Setup
    B[/Set up Repository\] --> B.1>"Identify and document technology, <br> software and data types (eg .md) <br> used in the project"]
    B.1 --> B1.1[.gitignore] --> B.EOG
    B.1 --> B1.2[Editorconfig] --> B.EOG
    B.1 --> B1.3[Repository Hooks] --> B.EOG
    B.1 --> B1.4["Local Config <br> (eg format on save)"] --> B.EOG
    B --> B.4[Documentation System] --> B.EOG
    B --> B.5[README] --> B.EOG
    B --> B.6[LICENSE] --> B.EOG
  end

    B.EOG[\Repo Setup <br> done/] --> C
    B.EOG --> D

  subgraph Project Setup
    C[/Create Project Structure\] --> C.1["Create new project (eg in IntelliJ) <br> and add it to the repository"]
  end

  subgraph Develop Architecture
    D[/Create System Architecture\] --> D.1[Create Architecture Diagrams]
  end
```
