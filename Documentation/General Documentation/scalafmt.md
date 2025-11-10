# Scalafmt in Our Project

## What is Scalafmt?

Scalafmt is an automatic code formatter for Scala that ensures consistent code style across the project.

## Why We Use It

For our Agile Hardware Development project, we use scalafmt to:
- Maintain consistent formatting across team members
- Avoid formatting debates during code reviews
- Automatically fix style issues before commits

## Implementation

### Plugin Setup

We added the scalafmt plugin to `project/plugins.sbt`:

```scala
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.0")
```

### Configuration

We created `.scalafmt.conf` in the project root with basic settings:

```scala
version = "3.7.3"
runner.dialect = scala213
maxColumn = 100
align.preset = most
continuationIndent.defnSite = 2
```

## Usage

### Format all code:
```bash
sbt scalafmt
```

### Check formatting without changing files:
```bash
sbt scalafmtCheck
```

### Format test files:
```bash
sbt test:scalafmt
```

That's it - run `sbt scalafmt` before committing to keep code consistently formatted.
