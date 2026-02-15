# Budget Model Tests

This directory contains tests for the AI automation budget tracking system.

## Running Tests

```bash
# Run all budget model tests
python3 tests/test_budget_model.py
```

## Test Coverage

The test suite validates:
- Budget loading and saving
- Authorization gate detection
- $5 milestone detection
- Cost calculation
- Budget updates
- Authorization flow
- Discord notification formatting
- Execution count calculations

## Expected Output

When all tests pass, you should see:

```
======================================================================
Budget Model Test Suite
======================================================================
Testing budget loading...
✅ Budget loading works

Testing authorization gate detection...
✅ Below gate: OK
✅ At gate: OK
✅ Above gate: OK

...

======================================================================
✅ All tests passed!
======================================================================
```
