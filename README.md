# Simple mutational fuzzer

The fuzzer mutates a valid seed file using custom mutators and sends the generated input strings to the specified
command line program, monitoring their output and exit codes.

## Run fuzzer

```shell
java Fuzzer.java "./html_parser_mac_universal" # or "html_parser_win_x86_64.exe" on Windows or "./html_parser_linux_x86_64" on Linux
```
