# Morpheus

Generate dependency graph(s) for variables.

## Usage

Add an alias for `morpheus` in your deps.edn.

Run it in your project, provide directory to generate output files into -- directory needs to exist, format -- png, svg and dot is supported, latter is default -- and a list of paths to analyse.

Morpheus will generate a file per project variable with its dependency graph where nodes are other variables in the project or in one of the dependencies of the project.

```
clj -A:morpheus -m thomasa.morpheus.main -d graphs2 -f png src test
```

Uses [clj-kondo](https://github.com/borkdude/clj-kondo)
