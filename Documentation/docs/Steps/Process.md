# Step documentation for Process

## Run templated process
Execute an (ETL) process using the engine configured in a CrossTest process server configuration

### Sentences
| Type          | Language         | Sentence      |
|:---           |:---              |:---           |
| When | en | ^I run the ([a-zA-Z0-9_@$#]+) process ([a-zA-Z0-9_@$#]+)$ |

### Arguments
| Parameter    | Datatype          | Description          |
|:---          |:---               |:---                  |
|process config | String | Name of the process config |
|process name | String | Name of the process |

### Examples


```gherkin
 When I run the demo process load_Customer
```