# Workflow for Software Projects

## Todos

### Fundamentals

- Decide on project goal
- Decide on project name
- Decide on the technology
- Decide on team name
- Decide on workflow

### Setup

- Add Repo
- Add .gitignore
- Add Editorconfig

### Graph Legend

- Circles: Start and Stop of compelete graph
- Boxes: Do
- Down Trapezoid: End of graph/Section
- Flag: Topic of Discussion, requires documentation on done
- Rhombus: Questions/Decision

```mermaid
graph TD
  Start((Start)) --> A{Decide}

  subgraph Fundamental Decisions
    A --> A.1>Project Goal] --> A.EOG
    A --> A.2>Project Name] --> A.EOG
    A --> A.3>Technology] --> A.EOG
    A --> A.4>Team Name] --> A.EOG
  end

  A.EOG[\Fundamentals done/] --> B[Setup Repository]

  subgraph Repo Setup
    B --> B.1[Set up .gitignore]
    B --> B.2[Set up Editorconfig]
    B --> B.3[Set up README]
    B --> B.4[Set up LICENSE]
  end
```
