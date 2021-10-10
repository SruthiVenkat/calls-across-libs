library(sjPlot)
df <- read.csv("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/RQ1-fields.tsv", sep='\t')
filteredDf <- df[df$Visibility != "public", ]
aggDf <- aggregate(filteredDf$Count, by=list(filteredDf$FieldLibrary,filteredDf$Visibility), FUN=length)
tab_df(
  aggDf,
  title = "Reflective Fields",
  footnote = NULL,
  col.header = c("Library", "Visibility", "Count"),
  sort.column = 1,
  CSS = list(css.centeralign = 'text-align: left;')
)

