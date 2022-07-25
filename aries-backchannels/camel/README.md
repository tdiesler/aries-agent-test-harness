## Build ACA-Py + Camel Agents

```
./manage build -a acapy -a camel
```

## Start/Stop the TestHarness

```
./manage start -d acapy -b camel
...
./manage stop
```

# Run Tests

```
# Run a basic RFC0160 connection test using ACA-Py for Acme, Bob, Faber and Mallory
./manage test -d acapy -b camel -t @T001-RFC0160
```
