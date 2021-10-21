library(sjPlot)
library(xtable)
library(stringr)

file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="RQ4-api-proportion-*", full.names = TRUE)

#for (i in seq_along(file_list)) {
  filename = file_list[10]
  print(filename)
  df <- read.csv(filename, sep='\t')
  lib <- str_split(str_split(filename, "proportion-")[[1]][2], ".tsv")[[1]][1]
  
  tab_df(
    df,
    title = paste("API Proportion - ",lib, sep = ""),
    footnote = NULL,
    sort.column = 1,
    digits = 5,
    col.header = c("Client", "API Proportion", "No. Of Methods Used"),
    CSS = list(css.centeralign = 'text-align: left;')
  )
  
  print(xtable(df,
               caption = paste("API Proportion - ",lib, sep = ""), digits = 5, colnames(c("Client", "API Proportion", "No. Of Methods Used"))), 
        file = paste("Documents/Waterloo/PL/21.icse.library-usage/rq4-api-proportion-", lib, ".tex", sep = ""),size="small")
  
#}

