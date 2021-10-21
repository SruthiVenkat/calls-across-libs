library(sjPlot)
library(xtable)
library(stringr)

df <- read.csv("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ1-invocations.tsv", sep='\t')

getVersionlessLibs <- function(column) {
  calleeLibs <- c()
  for (i in column) {
    calleeLibGAV = strsplit(i, ":")
    if(length(calleeLibGAV[[1]])>=3)
      calleeLib <- paste(calleeLibGAV[[1]][[1]], calleeLibGAV[[1]][[2]], sep=":")
    else
      calleeLib <- calleeLibGAV[[1]][[1]]
    print(calleeLib)
    calleeLibs = c(calleeLibs,calleeLib)
  }
  return(calleeLibs)
}

df$ActualCalleeLibrary <- getVersionlessLibs(df$ActualCalleeLibrary)
df$CallerLibrary <- getVersionlessLibs(df$CallerLibrary)
df = df[!duplicated(df$ActualCalleeMethod),]

# reflective invocations - client to lib
aggDf <- aggregate(df$Count, by=list(df$ActualCalleeLibrary, df$CallerLibrary, df$CalleeVisibility), FUN=length)
totalsDf <- aggregate(df$Count, by=list(df$ActualCalleeLibrary, df$CallerLibrary), FUN=length)
mergedDf <- merge(aggDf, totalsDf, by=c(1,2))

counts <- hash()
for( i in rownames(mergedDf) ) {
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
  row = data.frame(list("Library"=str_split(lib, pattern=" ")[[1]][1], "Client"=str_split(lib, pattern=" ")[[1]][2], "default"=counts[[lib]][["default"]], "private"=counts[[lib]][["private"]], 
             "protected"=counts[[lib]][["protected"]], "public"=counts[[lib]][["public"]], "total"=counts[[lib]][["total"]]), check.names = FALSE)
  finalDf = rbind(finalDf, row)
}
finalDf <- finalDf[grepl(":", finalDf$Library),]


tab_df(
  finalDf,
  title = "Reflective Invocations",
  footnote = NULL,
  col.header = c("Library", "Visibility", "Count", "Total"),
  sort.column = 1,
  CSS = list(css.centeralign = 'text-align: left;')
)

print(xtable(finalDf,
       caption = "Reflective Invocations", digits = 0, colnames(c("Library", "Visibility", "Count", "Total"))), 
      file = "Documents/Waterloo/PL/21.icse.library-usage/rq1a-reflective-invocations.tex",size="small")

