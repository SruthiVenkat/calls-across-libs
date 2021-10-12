library(sjPlot)
df <- read.csv("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ1-classUsage.tsv", sep='\t')
aggDf <- aggregate(df$Count, by=list(df$ClassLibrary,df$ClassVisibility), FUN=length)
totalsDf <- aggregate(df$Count, by=list(df$ClassLibrary), FUN=length)
mergedDf <- merge(aggDf, totalsDf, by=1)
print(aggDf)
counts <- hash()
for( i in rownames(mergedDf) ) {
  if(is.null(counts[[mergedDf[i, "Group.1"]]])) {
    counts[mergedDf[i, "Group.1"]] <- hash()
    counts[[mergedDf[i, "Group.1"]]][["default"]] = 0
    counts[[mergedDf[i, "Group.1"]]][["private"]] = 0
    counts[[mergedDf[i, "Group.1"]]][["protected"]] = 0
    counts[[mergedDf[i, "Group.1"]]][["public"]] = 0
    counts[[mergedDf[i, "Group.1"]]][["total"]] = mergedDf[i, "x.y"]
    counts[[mergedDf[i, "Group.1"]]][[mergedDf[i, "Group.2"]]] = mergedDf[i, "x.x"]
  } else {
    counts[[mergedDf[i, "Group.1"]]][[mergedDf[i, "Group.2"]]] = mergedDf[i, "x.x"]
    counts[[mergedDf[i, "Group.1"]]][["total"]] = mergedDf[i, "x.y"]
  }
}

finalDf <- data.frame("Library"=character(), "default"=integer(), "private"=integer(), "protected"=integer(), "public"=integer(), "total"=integer())
for (lib in keys(counts)) {
  if (is.null(counts[[lib]][["protected"]])) counts[[lib]][["protected"]] = 0
  if (is.null(counts[[lib]][["public"]])) counts[[lib]][["public"]] = 0
  if (is.null(counts[[lib]][["default"]])) counts[[lib]][["default"]] = 0
  if (is.null(counts[[lib]][["total"]])) counts[[lib]][["total"]] = 0
  row = data.frame(list("Library"=lib, "default"=counts[[lib]][["default"]], "private"=counts[[lib]][["private"]], 
                        "protected"=counts[[lib]][["protected"]], "public"=counts[[lib]][["public"]], "total"=counts[[lib]][["total"]]), check.names = FALSE)
  finalDf = rbind(finalDf, row)
}


tab_df(
  finalDf,
  title = "Class Usage",
  footnote = NULL,
  col.header = c("Library", "Visibility", "Count", "Total"),
  sort.column = 1,
  CSS = list(css.centeralign = 'text-align: left;')
)

print(xtable(finalDf,
       caption = "Class Usage", digits = 0, colnames(c("Library", "Visibility", "Count", "Total"))), 
      file = "Documents/Waterloo/PL/21.icse.library-usage/rq1a-class-usage.tex",size="small")
