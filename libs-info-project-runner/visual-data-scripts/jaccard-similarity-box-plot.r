# Jaccard Similarity = intersection/union
library(hash)
library(ggplot2)  

libraries <- c("com.alibaba:fastjson", "commons-collections:commons-collections", "commons-io:commons-io", "joda-time:joda-time", 
               "com.google.code.gson:gson", "org.json:json", "org.jsoup:jsoup", "org.slf4j:slf4j-api", "com.fasterxml.jackson.core:jackson-databind", "com.fasterxml.jackson.core:jackson-core")
#libraries <- c("commons-collections:commons-collections")
callee_methods <- hash() # client -> (library -> [list of methods])

file_list = list.files(path="/Users/sruthivenkat/Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-invocations.tsv", full.names = TRUE)

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
    if(is.null(callee_methods[[calleeLib]])) {
      #callee_methods[[callerLib]] = c(df[i,6])
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

jacSimilarities <- hash()
for (lib in libraries){
  if(lib == "" || is.null(callee_methods[[lib]])){
    next
  }
  if(is.null(jacSimilarities[[lib]])){
    jacSimilarities[lib] <- hash()
  }
  path <- paste("/Users/sruthivenkat/Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/visual-data/jaccard-similarities/jaccard-declared-",lib,".tsv",sep="")
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

#boxplot here
libraryAggregationData = hash()
jacSims = c()
libsForPlot = c()
uniqueLibs = c()
for (lib in libraries){
  numRowsParsed <- 0
  if(lib != ""){
    for (clientA in sort(keys(callee_methods[[lib]]))){
      numColumnsParsed <- 0
      for (clientB in sort(keys(callee_methods[[lib]]))){
        numColumnsParsed = numColumnsParsed + 1
        if(numRowsParsed >= numColumnsParsed){
          next
        }
        if(clientA != clientB){
          libsForPlot = c(libsForPlot,lib)
          jacSims = c(jacSims,jacSimilarities[[lib]][[clientA]][[clientB]])
        }
      }
      numRowsParsed = numRowsParsed + 1
    }
  }
}

for (lib in libraries){
  if(lib != ""){
   uniqueLibs = c(uniqueLibs, lib)
  }
}

dfToPlot = data.frame(libsForPlot, jacSims)
names(dfToPlot) <- c('Library','JaccardSim')
print(dfToPlot)
dfToPlot$Library <- as.factor(dfToPlot$Library)
dfToPlot$Library <- factor(dfToPlot$Library, levels=uniqueLibs, labels=uniqueLibs)
GPlot1 = ggplot(dfToPlot, aes(x = Library, y=JaccardSim)) + geom_boxplot() + theme(text = element_text(size=10),
        axis.text.x = element_text(angle=90, hjust=1)) 
ggsave(
  "Documents/Waterloo/PL/21.icse.library-usage/jac-sim-box-plot.png",
  plot = GPlot1,
  device = "png")

# compare all clients
finalDf <- data.frame("Library"=character(), "Total No. of clients"=integer(), "% Clients calling same methods"=double(), 
                      "No. of methods called by (%) clients"=integer(), check.names = FALSE)
for (lib in libraries) {
  methodsToNoOfClients <- hash()
  intersectingMethods <- list()
  for (client in keys(callee_methods[[lib]])) {
    print(callee_methods[[lib]][[client]])
    intersectingMethods <- intersect(intersectingMethods, callee_methods[[lib]][[client]])
    print(intersectingMethods)
    for (method in callee_methods[[lib]][[client]]) {
      if (!is.na(method)) {
        if(is.null(methodsToNoOfClients[[method]]))
          methodsToNoOfClients[[method]] <- 1
        else
          methodsToNoOfClients[[method]] <- methodsToNoOfClients[[method]] + 1
      }
    }
  }
  row = data.frame(list("Library"=lib, "Total No. of clients"=length(callee_methods[[lib]]), "% Clients calling same methods"=100.0*max(values(methodsToNoOfClients))/length(callee_methods[[lib]]), 
                        "No. of methods called by (%) clients"=length(invert(methodsToNoOfClients)[[toString(max(values(methodsToNoOfClients)))]])), 
                   check.names = FALSE)
  finalDf = rbind(finalDf, row)
  #print(paste("Library", lib, length(callee_methods[[lib]]), max(values(methodsToNoOfClients)), 
  #            100.0*max(values(methodsToNoOfClients))/length(callee_methods[[lib]]), length(invert(methodsToNoOfClients)[[toString(max(values(methodsToNoOfClients)))]])))
}

getArtifactNames <- function(column) {
  artNames <- c()
  for (i in column) {
    artGAV = strsplit(i, ":")
    if(length(artGAV[[1]])>=2)
      art <- artGAV[[1]][[2]]
    else
      art <- artGAV[[1]][[1]]
    artNames = c(artNames,art)
  }
  return(artNames)
}
finalDf$Library <- getArtifactNames(finalDf$Library)


tab_df(
  finalDf,
  title = "% of clients calling same methods",
  footnote = NULL,
  sort.column = 1,
  CSS = list(css.centeralign = 'text-align: right;')
)
print(xtable(finalDf, align = c('l','r','r','r','r'),
             caption = "\\% of clients calling same methods", digits = c(0,0,0,2,0)),
      file = "Documents/Waterloo/PL/21.icse.library-usage/tables/results/perc-clients-same-methods.tex",size="small",include.rownames = FALSE)

