library(dplyr)
library(rlist)

# Reflective Invocations
file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-invocations.tsv", full.names = TRUE)

for (i in seq_along(file_list)) {
  filename = file_list[[i]]
  df <- read.csv(filename, sep='\t')
  for(i in 1:nrow(df)) 
  {
    #df$Actual.Callee.Method # add for each client based on lib (client-caller lib)
    if (is.character(df[i, 2]) && is.character(df[i, 7])) {
      calleeLibGAV = strsplit(df[i,7], ":")
      if(length(calleeLibGAV[[1]])>=3)
        calleeLib <- paste(calleeLibGAV[[1]][[1]], calleeLibGAV[[1]][[2]], sep=":")
      else
        calleeLib <- calleeLibGAV[[1]][[1]]
      callerLibGAV = strsplit(df[i,2], ":")
      if(length(callerLibGAV[[1]])>=3)
        callerLib <- paste(callerLibGAV[[1]][[1]], callerLibGAV[[1]][[2]], sep=":")
      else
        callerLib <- callerLibGAV[[1]][[1]]
    }
    if (grepl("commons-collections", calleeLib, fixed = TRUE))
      calleeLib <- "org.apache.commons:commons-collections4"
    if (grepl("commons-collections", callerLib, fixed = TRUE))
      callerLib <- "org.apache.commons:commons-collections4"
    if(is.null(callee_methods[[calleeLib]])) {
      callee_methods[calleeLib] <- hash()
      callee_methods[[calleeLib]][[callerLib]] = c(df[i,4])
    } else {
      if(is.null(callee_methods[[calleeLib]][[callerLib]])) {
        callee_methods[[calleeLib]][[callerLib]] = c(df[i,4])
      } else {
        callee_methods[[calleeLib]][[callerLib]] = c(callee_methods[[calleeLib]][[callerLib]], df[i,4])
      }
    }
    
    callee_methods[[calleeLib]][[callerLib]] = as.list(unique(callee_methods[[calleeLib]][[callerLib]]))
    
    
  }
}
df <- data.frame("Library"=character(), "Client"=character(),"Count"=integer())
internals <- hash()
for (lib in keys(callee_methods)) {
  for (client in keys(callee_methods[[lib]])) {
    for (method in callee_methods[[lib]][[client]]) {
      if (grepl( "internal", method, fixed = TRUE)) {
        key <- paste(lib,client)
        if (is.null(internals[[key]])) internals[[key]] = 1
        else internals[[key]] = internals[[key]] + 1
      }
    }
  }
}
print()

