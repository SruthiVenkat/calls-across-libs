library(dplyr)

# Reflective Invocations
file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-invocations.tsv", full.names = TRUE)
writeLines("CallerLibrary\tActualCalleeLibrary\tActualCalleeMethod\tCalleeVisibility\tCounts","Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ1-invocations.tsv")
for (i in seq_along(file_list)) {
  filename = file_list[[i]]
  df <- read.csv(filename, sep='\t')
  # Extract reflective calls
  reflDf <- df[df$Reflective == "true", ]
  counts = reflDf %>% group_by(Caller.Library, Actual.Callee.Library, Actual.Callee.Method,Callee.Visibility) %>% summarise(Count = n())
  print(counts)
  write.table(counts,"Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ1-invocations.tsv",sep="\t",row.names=FALSE, col.names=FALSE, append=TRUE)
}

# Reflective Fields
file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-fields.tsv", full.names = TRUE)
writeLines("ClientLibrary\tFieldLibrary\tFieldName\tFieldSignature\tVisibility\tStatic\tCounts","Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ1-fields.tsv")
for (i in seq_along(file_list)) {
  filename = file_list[[i]]
  print(filename)
  df <- read.csv(filename, sep='\t')
  # Extract reflective calls
  reflDf <- df[df$Reflective == "true", ]
  counts = reflDf %>% group_by(Callee.Library, Field.Library, Field.Name, Field.Signature, Visibility, Static) %>% summarise(Count = n())
  print(counts)
  write.table(counts,"Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ1-fields.tsv",sep="\t",row.names=FALSE, col.names=FALSE, append=TRUE)
  
}

# Class Usage
file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-classesUsageInfo.tsv", full.names = TRUE)
writeLines("UsedInLibrary\tClassLibrary\tClassName\tClassVisibility\tUsage\tCounts","Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ1-classUsage.tsv")
for (i in seq_along(file_list)) {
  filename = file_list[[i]]
  print(filename)
  df <- read.csv(filename, sep='\t')
  # Extract reflective calls
  reflDf <- df[df$Class.Visibility != "public", ]
  counts = reflDf %>% group_by(Used.In.Library, Class.Library, Class.Name, Class.Visibility, Usage) %>% summarise(Count = n())
  print(counts)
  write.table(counts,"Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ1-classUsage.tsv",sep="\t",row.names=FALSE, col.names=FALSE, append=TRUE)
}

