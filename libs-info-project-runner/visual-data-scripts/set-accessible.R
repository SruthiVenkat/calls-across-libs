library(sjPlot)
library(xtable)
library(stringr)

df <- read.csv("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/visual-data/RQ2-setAccessibleCalls.tsv", sep='\t')

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

df$CalleeLibrary <- getVersionlessLibs(df$CalleeLibrary)
df$CallerLibrary <- getVersionlessLibs(df$CallerLibrary)
df = df[!duplicated(c(df$CalleeName, df$FieldSignature)),]

# setAccessible
aggDf <- aggregate(df$Count, by=list(df$CalleeLibrary, df$CallerLibrary, df$setAccessible.CalledOn, df$Visibility), FUN=length)
totalsDf <- aggregate(df$Count, by=list(df$CalleeLibrary, df$CallerLibrary, df$setAccessible.CalledOn), FUN=length)
mergedDf <- merge(aggDf, totalsDf, by = c("Group.1","Group.2","Group.3"))

counts <- hash()
for( i in rownames(mergedDf) ) {
  callerCallee <- paste(mergedDf[i, "Group.1"], mergedDf[i, "Group.2"], mergedDf[i, "Group.3"])
  if(!(callerCallee %in% keys(counts))) {
    counts[[callerCallee]] <- hash()
    counts[[callerCallee]][["default"]] = 0
    counts[[callerCallee]][["private"]] = 0
    counts[[callerCallee]][["protected"]] = 0
    counts[[callerCallee]][["public"]] = 0
    counts[[callerCallee]][["total"]] = mergedDf[i, "x.y"]
    counts[[callerCallee]][[mergedDf[i, "Group.4"]]] = mergedDf[i, "x.x"]
  } else {
    counts[[callerCallee]][[mergedDf[i, "Group.4"]]] = mergedDf[i, "x.x"]
    counts[[callerCallee]][["total"]] = mergedDf[i, "x.y"]
  }
}

finalDf <- data.frame("Library"=character(), "Client"=character(), "Object"=character(), "default"=integer(), "private"=integer(), "protected"=integer(), "public"=integer(), "total"=integer(), check.names = FALSE)
for (lib in keys(counts)) {
  print(lib)
  if (is.null(counts[[lib]][["protected"]])) counts[[lib]][["protected"]] = 0
  if (is.null(counts[[lib]][["public"]])) counts[[lib]][["public"]] = 0
  if (is.null(counts[[lib]][["default"]])) counts[[lib]][["default"]] = 0
  if (is.null(counts[[lib]][["total"]])) counts[[lib]][["total"]] = 0
  row = data.frame(list("Library"=str_split(lib, pattern=" ")[[1]][1], "Client"=str_split(lib, pattern=" ")[[1]][2], "Object"=str_split(lib, pattern=" ")[[1]][3], "default"=counts[[lib]][["default"]], "private"=counts[[lib]][["private"]], 
                        "protected"=counts[[lib]][["protected"]], "public"=counts[[lib]][["public"]], "total"=counts[[lib]][["total"]]), check.names = FALSE)
  print(finalDf)
  print(row)
  finalDf = rbind(finalDf, row)
}

tab_df(
  finalDf,
  title = "setAccessible Calls",
  footnote = NULL,
  col.header = c("Library", "Visibility", "Count", "Total"),
  sort.column = 1,
  CSS = list(css.centeralign = 'text-align: left;')
)

print(xtable(finalDf,
             caption = "setAccessible Calls", digits = 0, colnames(c("Library", "Visibility", "Count", "Total"))), 
      file = "Documents/Waterloo/PL/21.icse.library-usage/rq1b-set-accessible.tex",size="small")

