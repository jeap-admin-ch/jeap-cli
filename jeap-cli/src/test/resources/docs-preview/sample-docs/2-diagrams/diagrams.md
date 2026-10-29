# Diagrams

## PlantUML

```plantuml
@startuml
actor Writer
participant "jEAP CLI" as cli
participant Browser
Writer -> cli : jeap docs preview
cli -> Browser : open http://127.0.0.1:4000
Writer -> cli : edit docs/*.md
cli -> Browser : reload
@enduml
```

## Mermaid

```mermaid
flowchart LR
    PR[Pull request with 'preview' in the title] --> WF[jeap-doc-preview workflow]
    WF --> CLI[jeap docs build]
    CLI --> Pages[GitHub Pages, private]
    Pages --> Comment[Link posted on the PR]
```

## Graphviz

```dot
digraph G {
  rankdir=LR;
  node [shape=box, style=rounded];
  "docs/*.md" -> "commonmark-java" -> "HTML page";
  "fenced diagrams" -> "browser (mermaid.js, @plantuml/core)";
}
```

## PlantUML with a theme

```plantuml
@startuml
!theme plain
Writer -> Browser : read
@enduml
```

## A diagram with a script link

The preview removes the `javascript:` link from the rendered SVG.

```dot
digraph G {
  link [label="Click me", href="javascript:alert(1)"];
}
```

## A broken diagram

```plantuml
@startuml
Alice -> Bob : fine
this line is not PlantUML
@enduml
```

## Ordinary code

```java
System.out.println("Not a diagram");
```
