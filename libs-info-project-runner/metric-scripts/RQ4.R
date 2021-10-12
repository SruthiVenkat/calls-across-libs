library(hash)
libraries <- c("com.alibaba:fastjson", "org.apache.commons:commons-collections4", "commons-io:commons-io", "joda-time:joda-time", 
               "com.google.code.gson:gson", "org.json:json", "org.jsoup:jsoup", "org.slf4j:slf4j-api", "com.fasterxml.jackson.core:jackson-databind", "com.fasterxml.jackson.core:jackson-core")

callee_methods <- hash() # library -> (client -> [list of methods])

file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-invocations.tsv", full.names = TRUE)

for (i in seq_along(file_list)) {
  filename = file_list[[i]]
  df <- read.csv(filename, sep='\t')
  for(i in 1:nrow(df)) 
  {
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
    if(is.null(callee_methods[[calleeLib]])) {
      callee_methods[calleeLib] <- hash()
      callee_methods[[calleeLib]][[callerLib]] = c(df[i,6])
    } else {
      if(is.null(callee_methods[[calleeLib]][[callerLib]])) {
        callee_methods[[calleeLib]][[callerLib]] = c(df[i,6])
      } else {
        callee_methods[[calleeLib]][[callerLib]] = c(callee_methods[[calleeLib]][[callerLib]], df[i,6])
      }
    }
    
    callee_methods[[calleeLib]][[callerLib]] = as.list(unique(callee_methods[[calleeLib]][[callerLib]]))
    
    
  }
}

libsInfoList = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-libsInfo.tsv", full.names = TRUE)


# Jaccard Similarity = intersection/union
jacSimilarities <- hash()
for (lib in libraries){
  if(lib == "" || is.null(callee_methods[[lib]])){
    next
  }
  if(is.null(jacSimilarities[[lib]])){
    jacSimilarities[lib] <- hash()
  }
  path <- paste("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ4-jaccard-",lib,".tsv",sep="")
  cat("Client A/Client B",file=path,sep="")
  for (clientA in keys(callee_methods[[lib]])){
    if(is.null(jacSimilarities[[lib]][[clientA]])){
      jacSimilarities[[lib]][[clientA]] <- hash()
    }
    for (clientB in keys(callee_methods[[lib]])){
      jacSimilarities[[lib]][[clientA]][[clientB]] = length(intersect(callee_methods[[lib]][[clientA]],callee_methods[[lib]][[clientB]]))/length(union(callee_methods[[lib]][[clientA]],callee_methods[[lib]][[clientB]]))
    }
  }
  
  cat("\t",file=path,sep="",append=TRUE)
  i <- 0
  for(row in sort(keys(jacSimilarities[[lib]]))) {
    if(i == length(keys(jacSimilarities[[lib]])) - 1){
      cat(row,file=path,sep="",append=TRUE)
    } else {
      cat(paste(row,"\t"),file=path,sep="",append=TRUE)
    }
    i = i+1
  }
  for(row in sort(keys(jacSimilarities[[lib]]))) {
    cat(paste("\n",row,"\t",sep=""),file=path,sep="",append=TRUE)
    i <- 0
    for(column in sort(keys(jacSimilarities[[lib]]))) {
      if(i == length(keys(jacSimilarities[[lib]])) - 1){
        cat(jacSimilarities[[lib]][[row]][[column]],file=path,sep="",append=TRUE)
      } else {
        cat(paste(jacSimilarities[[lib]][[row]][[column]],"\t",sep=""),file=path,sep="",append=TRUE)
      }
      i = i+1
    }
  }
}

# API Proportions
apiProportions <- hash()
for (lib in libraries){
  if(lib == "" || is.null(callee_methods[[lib]])){
    next
  }
  if(is.null(jacSimilarities[[lib]])){
    apiProportions[lib] <- hash()
  }
  path <- paste("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ4-api-proportion-",lib,".tsv",sep="")

  cat("Client\tAPI Proportion",file=path,sep="")
  for (client in keys(callee_methods[[lib]])){
    #apiProportions[[lib]][[client]] <- 
    print(paste("lib",lib,"client",client,length(callee_methods[[lib]][[client]]),sep=" "))
  }
}
