import sys
from pathlib import Path

# Make `import airpoint` and `from test_pointer import ...` work without installing.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))
