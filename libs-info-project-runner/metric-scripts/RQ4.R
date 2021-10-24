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
    if (grepl("commons-collections", calleeLib, fixed = TRUE))
      calleeLib <- "org.apache.commons:commons-collections4"
    if (grepl("commons-collections", callerLib, fixed = TRUE))
      callerLib <- "org.apache.commons:commons-collections4"
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

# get total number of public/protected methods of each library
totalMethods <- hash()
testMethods <- hash()
totalFields <- hash()
totalClasses <- hash()
totalAnnotations <- hash()

libsInfoList = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-libsInfo.tsv", full.names = TRUE)
for (i in seq_along(libsInfoList)) {
  print(libsInfoList[[i]])
  libsInfoFile = libsInfoList[[i]]
  subdirs = str_split(libsInfoFile,"/")
  gav = strsplit(subdirs[[1]][length(subdirs[[1]])-1], ":")
  file = paste(gav[[1]][[1]], gav[[1]][[2]], sep=":")
  print(file)
  if (file %in% libraries) {
    libsDf <- read.csv(libsInfoFile, sep='\t')
    for(j in 1:nrow(libsDf)) {
      if(startsWith(libsDf[j, "Library.Name"], file)) {
        totalMethods[file] = libsDf[j, 2]
        totalClasses[file] = libsDf[j, 3]
        totalFields[file] = libsDf[j, 4]
        totalAnnotations[file] = libsDf[j, 5]
        testMethods[file] = libsDf[j, 6]
      }
    }
  }
}

# API Proportions
apiProportions <- hash()
for (lib in libraries){
  if(lib == "" || is.null(callee_methods[[lib]])){
    next
  }
  if(is.null(apiProportions[[lib]])){
    apiProportions[lib] <- hash()
  }
  path <- paste("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ4-api-proportion-",lib,".tsv",sep="")

  cat("Client\tAPI Proportion\tNo. Of Methods",file=path,sep="")
  for (client in keys(callee_methods[[lib]])){
    print(paste("lib",lib,"client",client,1.0*length(callee_methods[[lib]][[client]])/totalMethods[[lib]],sep=" "))
    cat("\n",paste(client,1.0*length(callee_methods[[lib]][[client]])/totalMethods[[lib]],length(callee_methods[[lib]][[client]]),sep="\t"),file=path,sep="",append=TRUE)
  }
}

libsDetailsDf <- data.frame("Library"=character(), "No. Of Public/Protected Methods"=integer(), "No. Of Methods Called By Library Tests"=integer(), "No. Of Methods Called By All Clients"=integer(), "(methods called by library's tests)/total"=double())
for (lib in libraries) {
  allMethods <- c()
  for (client in keys(callee_methods[[lib]])) {
    allMethods <- c(allMethods, callee_methods[[lib]][[client]])
  }
  allMethods = as.list(unique(allMethods))
  #todo - look into these libs
  if (testMethods[[lib]]>totalMethods[[lib]]) testMethods[[lib]] = 0
  row = data.frame(list("Library"=lib, "No. Of Public/Protected Methods"=totalMethods[[lib]], "No. Of Methods Called By Library Tests"=testMethods[[lib]], "No. Of Methods Called By All Clients"=length(allMethods), "(methods called by library's tests)/total"=1.0*testMethods[[lib]]/totalMethods[[lib]]))
  libsDetailsDf = rbind(libsDetailsDf, row)
}




tab_df(
  libsDetailsDf,
  title = "Library Details",
  footnote = NULL,
  col.header = c("Library", "No. Of Public/Protected Methods", "No. Of Methods Called By Library Tests", "No. Of Methods Called By All Clients", "(methods called by library's tests)/total"),
  sort.column = 1,
  CSS = list(css.centeralign = 'text-align: left;')
)
print(xtable(libsDetailsDf,
             caption = "Library Details", digits = 5, colnames(c("Library", "No. Of Public/Protected Methods", "No. Of Methods Called By Library Tests", "No. Of Methods Called By All Clients", "(methods called by library's tests)/total"))), 
      file = "Documents/Waterloo/PL/21.icse.library-usage/rq4-library-details.tex", size="small")

