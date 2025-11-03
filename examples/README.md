Examples

This folder is reserved for example sites materialized from the generator templates.

How to materialize locally
- Build the generator: bash generator/scripts/build-jvm.sh
- Create a working dir (e.g., examples/site) and copy templates:
  - mkdir -p examples/site
  - rsync -a generator/src/main/resources/templates/ examples/site/
- Optionally run the generator over it:
  - generator/build/bloggen build --src examples/site --out dist

Note: The root HTML files are demo-only; the generator templates are the source of truth.

