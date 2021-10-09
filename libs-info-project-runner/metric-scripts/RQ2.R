library(dplyr)

file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-invocations.tsv", full.names = TRUE)
writeLines("CallerLibrary\tDeclaredCalleeLibrary\tActualCalleeLibrary\tDeclaredCalleeMethod\tActualCalleeMethod\tCalleeVisibility\tServiceBypass\tCounts","Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ2-serviceBypass.tsv")
for (i in seq_along(file_list)) {
  filename = file_list[[i]]
  df <- read.csv(filename, sep='\t')
  # RQ2a - ServiceLoader + Casts
  reflDf <- df[df$Service.Bypass == "cast", ]
  counts = reflDf %>% group_by(Caller.Library, Declared.Callee.Library, Actual.Callee.Library, Declared.Callee.Method, Actual.Callee.Method, Callee.Visibility,Service.Bypass) %>% summarise(Count = n())
  print(counts)
  write.table(counts,"Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ2-serviceBypass.tsv",sep="\t",row.names=FALSE, col.names=FALSE, append=TRUE)
  
  # RQ2b - ServiceLoader + Instantiations
  reflDf <- df[df$Service.Bypass == "instantiation", ]
  counts = reflDf %>% group_by(Caller.Library, Declared.Callee.Library, Actual.Callee.Library, Declared.Callee.Method, Actual.Callee.Method, Callee.Visibility,Service.Bypass) %>% summarise(Count = n())
  print(counts)
  write.table(counts,"Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ2-serviceBypass.tsv",sep="\t",row.names=FALSE, col.names=FALSE, append=TRUE)
  
  # RQ2c - ServiceLoader + Reflection
  reflDf <- df[df$Service.Bypass == "reflection", ]
  counts = reflDf %>% group_by(Caller.Library, Declared.Callee.Library, Actual.Callee.Library, Declared.Callee.Method, Actual.Callee.Method, Callee.Visibility,Service.Bypass) %>% summarise(Count = n())
  print(counts)
  write.table(counts,"Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ2-serviceBypass.tsv",sep="\t",row.names=FALSE, col.names=FALSE, append=TRUE)
}

# RQ2 - serviceBypassCalls
file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-serviceBypassCalls.tsv", full.names = TRUE)
writeLines("CallerLibrary\tInterfaceLibrary\tInterfaceName\tCallerMethod\tCalleeMethod\tImplName\tCounts","Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ2-serviceBypassCalls.tsv")
for (i in seq_along(file_list)) {
  filename = file_list[[i]]
  print(filename)
  df <- read.csv(filename, sep='\t')
  counts = df %>% group_by(Caller.Library, Interface.Library, Interface.Name, Caller.Method, Callee.Method, Impl..Name) %>% summarise(Count = n())
  print(counts)
  write.table(counts,"Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ2-serviceBypassCalls.tsv",sep="\t",row.names=FALSE, col.names=FALSE, append=TRUE)
}

# RQ2d - setAccessible
file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-setAccessibleCalls.tsv", full.names = TRUE)
writeLines("CallerLibrary\tCalleeLibrary\tCallerMethod\tsetAccessible.CalledOn\tVisibility\tCallerName\tFieldSignature\tCounts","Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ2-setAccessibleCalls.tsv")
for (i in seq_along(file_list)) {
  filename = file_list[[i]]
  print(filename)
  df <- read.csv(filename, sep='\t')
  counts = df %>% group_by(Caller.Library, Callee.Library, Caller.Method, setAccessible.Called.On, Visibility, Callee.Name, Field.Signature) %>% summarise(Count = n())
  print(counts)
  write.table(counts,"Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ2-setAccessibleCalls.tsv",sep="\t",row.names=FALSE, col.names=FALSE, append=TRUE)
}

