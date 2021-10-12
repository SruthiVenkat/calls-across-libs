library(sjPlot)
library(xtable)
library(stringr)

df <- read.csv("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ2-setAccessibleCalls.tsv", sep='\t')

# setAccessible
aggDf <- aggregate(df$Count, by=list(df$CalleeLibrary,df$setAccessible.CalledOn, df$Visibility), FUN=length)
totalsDf <- aggregate(df$Count, by=list(df$CalleeLibrary, df$setAccessible.CalledOn), FUN=length)
mergedDf <- merge(aggDf, totalsDf, by = c("Group.1","Group.2"))
print(mergedDf)
counts <- hash()
for( i in rownames(mergedDf) ) {
  lib <- mergedDf[i, "Group.1"]
  calledOn <- mergedDf[i, "Group.2"]
  if(!(paste(lib,calledOn) %in% keys(counts))) {
    counts[[paste(lib,calledOn)]] <- hash()
    counts[[paste(lib,calledOn)]][["default"]] = 0
    counts[[paste(lib,calledOn)]][["private"]] = 0
    counts[[paste(lib,calledOn)]][["protected"]] = 0
    counts[[paste(lib,calledOn)]][["public"]] = 0
    counts[[paste(lib,calledOn)]][["total"]] = mergedDf[i, "x.y"]
    counts[[paste(lib,calledOn)]][[mergedDf[i, "Group.3"]]] = mergedDf[i, "x.x"]
  } else {
    counts[[paste(lib,calledOn)]][[mergedDf[i, "Group.3"]]] = mergedDf[i, "x.x"]
    counts[[paste(lib,calledOn)]][["total"]] = mergedDf[i, "x.y"]
  }
}
print(counts)

finalDf <- data.frame("Library"=character(), "Object"=character(), "default"=integer(), "private"=integer(), "protected"=integer(), "public"=integer(), "total"=integer(), check.names = FALSE)
for (lib in keys(counts)) {
  print(lib)
  if (is.null(counts[[lib]][["protected"]])) counts[[lib]][["protected"]] = 0
  if (is.null(counts[[lib]][["public"]])) counts[[lib]][["public"]] = 0
  if (is.null(counts[[lib]][["default"]])) counts[[lib]][["default"]] = 0
  if (is.null(counts[[lib]][["total"]])) counts[[lib]][["total"]] = 0
  row = data.frame(list("Library"=str_split(lib, pattern=" ")[[1]][1], "Object"=str_split(lib, pattern=" ")[[1]][2], "default"=counts[[lib]][["default"]], "private"=counts[[lib]][["private"]], 
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

