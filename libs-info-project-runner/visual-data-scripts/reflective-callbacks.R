library(sjPlot)
library(xtable)
library(stringr)
library(dplyr)

getVersionlessLibs <- function(column) {
  calleeLibs <- c()
  for (i in column) {
    calleeLibGAV = strsplit(i, ":")
    if(length(calleeLibGAV[[1]])>=3)
      calleeLib <- paste(calleeLibGAV[[1]][[1]], calleeLibGAV[[1]][[2]], sep=":")
    else
      calleeLib <- calleeLibGAV[[1]][[1]]
    calleeLibs = c(calleeLibs,calleeLib)
  }
  return(calleeLibs)
}


# Reflective Callbacks
file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-invocations.tsv", full.names = TRUE)
writeLines("CallerLibrary\tActualCalleeLibrary\tActualCalleeMethod\tCalleeVisibility\tCounts","Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/visual-data/RQ1-reflective-callbacks.tsv")
for (i in seq_along(file_list)) {
  filename = file_list[[i]]
  print(filename)
  if (endsWith(filename,"RQ1-invocations.tsv"))
    next
  subdirs = str_split(filename,"/")
  client =subdirs[[1]][length(subdirs[[1]])-1]
  df <- read.csv(filename, sep='\t')
  # Extract reflective calls
  reflDf <- df[df$Reflective == "true", ]
  if (nrow(reflDf)==0) next
  
  reflDf$Caller.Library <- getVersionlessLibs(reflDf$Caller.Library)
  reflDf <- subset(reflDf, reflDf$Actual.Callee.Library==client)
 
  counts = reflDf %>% group_by(Caller.Library, Actual.Callee.Library, Actual.Callee.Method,Callee.Visibility) %>% summarise(Count = n())
  write.table(counts,"Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/visual-data/RQ1-reflective-callbacks.tsv",sep="\t",row.names=FALSE, col.names=FALSE, append=TRUE)
}

df <- read.csv("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/visual-data/RQ1-reflective-callbacks.tsv", sep='\t')


df$ActualCalleeLibrary <- getVersionlessLibs(df$ActualCalleeLibrary)
df = df[!duplicated(df$ActualCalleeMethod),]

# reflective invocations - client to lib
aggDf <- aggregate(df$Count, by=list(df$ActualCalleeLibrary, df$CallerLibrary, df$CalleeVisibility), FUN=length)
totalsDf <- aggregate(df$Count, by=list(df$ActualCalleeLibrary, df$CallerLibrary), FUN=length)
totalClientsDf <- aggregate(totalsDf$Group.2, by=list(totalsDf$Group.1), FUN=length)
mergedDf <- merge(aggDf, totalsDf, by=c(1,2))

counts <- hash()
for( i in rownames(mergedDf) ) {
  if (grepl("commons-collections", mergedDf[i, "Group.1"], fixed = TRUE))
    mergedDf[i, "Group.1"] <- "org.apache.commons:commons-collections4"
  if (grepl("commons-collections", mergedDf[i, "Group.2"], fixed = TRUE))
    mergedDf[i, "Group.2"] <- "org.apache.commons:commons-collections4"
  callerCallee <- paste(mergedDf[i, "Group.1"], mergedDf[i, "Group.2"])
  if(is.null(counts[[callerCallee]])) {
    counts[callerCallee] <- hash()
    counts[[callerCallee]][["default"]] = 0
    counts[[callerCallee]][["private"]] = 0
    counts[[callerCallee]][["protected"]] = 0
    counts[[callerCallee]][["public"]] = 0
    counts[[callerCallee]][["total"]] = mergedDf[i, "x.y"]
    counts[[callerCallee]][[mergedDf[i, "Group.3"]]] = mergedDf[i, "x.x"]
  } else {
    counts[[callerCallee]][[mergedDf[i, "Group.3"]]] = mergedDf[i, "x.x"]
    counts[[callerCallee]][["total"]] = mergedDf[i, "x.y"]
  }
}

finalDf <- data.frame("Library"=character(), "Client"=character(), "default"=integer(), "private"=integer(), "protected"=integer(), "public"=integer(), "total"=integer())
for (lib in keys(counts)) {
  if (is.null(counts[[lib]][["protected"]])) counts[[lib]][["protected"]] = 0
  if (is.null(counts[[lib]][["public"]])) counts[[lib]][["public"]] = 0
  if (is.null(counts[[lib]][["default"]])) counts[[lib]][["default"]] = 0
  if (is.null(counts[[lib]][["total"]])) counts[[lib]][["total"]] = 0
  row = data.frame(list("Library"=str_split(lib, pattern=" ")[[1]][2], "Client"=str_split(lib, pattern=" ")[[1]][1], "default"=counts[[lib]][["default"]], "private"=counts[[lib]][["private"]], 
                        "protected"=counts[[lib]][["protected"]], "public"=counts[[lib]][["public"]], "total"=counts[[lib]][["total"]]), check.names = FALSE)
  finalDf = rbind(finalDf, row)
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
finalDf$Client <- getArtifactNames(finalDf$Client)
tab_df(
  finalDf,
  title = "Reflective Callbacks",
  footnote = NULL,
  sort.column = 1,
  CSS = list(css.centeralign = 'text-align: left;')
)

print(xtable(finalDf,
             caption = "Reflective Callbacks", digits = 0), 
      file = "Documents/Waterloo/PL/21.icse.library-usage/tables/results/reflective-callbacks.tex",size="scriptsize",include.rownames = FALSE)

