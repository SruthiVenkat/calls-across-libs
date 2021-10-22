library(hash)
library(stringr)
library(sjPlot)
library(xtable)

libraries <- c("com.alibaba:fastjson", "org.apache.commons:commons-collections4", "commons-io:commons-io", "joda-time:joda-time", 
               "com.google.code.gson:gson", "org.json:json", "org.jsoup:jsoup", "org.slf4j:slf4j-api", "com.fasterxml.jackson.core:jackson-databind", "com.fasterxml.jackson.core:jackson-core")

libsInfoList = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data-2", recursive = TRUE, pattern="*-libsInfo.tsv", full.names = TRUE)

totalMethods <- hash()
testMethods <- hash()
totalFields <- hash()
totalClasses <- hash()
totalAnnotations <- hash()

for (i in seq_along(libsInfoList)) {
  libsInfoFile = libsInfoList[[i]]
  print(libsInfoFile)
  libsDf <- read.csv(libsInfoFile, sep='\t')
  subdirs = str_split(libsInfoFile,"/")
  gav = strsplit(subdirs[[1]][length(subdirs[[1]])-1], ":")
  file = paste(gav[[1]][[1]], gav[[1]][[2]], sep=":")
  if (file %in% libraries) {
    for(j in 1:nrow(libsDf)) {
      if(startsWith(libsDf[j, "Library.Name"], file)) {
        totalMethods[file] = libsDf[j, 2]
        totalClasses[file] = libsDf[j, 3]
        totalFields[file] = libsDf[j, 4]
        totalAnnotations[file] = libsDf[j, 5]
        testMethods[file] = libsDf[j, 6]
        break
      }
    }
  }
}

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

callee_fields <- hash() # library -> (client -> [list of methods])
file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-fields.tsv", full.names = TRUE)
for (i in seq_along(file_list)) {
  filename = file_list[[i]]
  df <- read.csv(filename, sep='\t')
  for(i in 1:nrow(df)) 
  {
    if (is.character(df[i, 1]) && is.character(df[i, 8])) {
      calleeLibGAV = strsplit(df[i,8], ":")
      if(length(calleeLibGAV[[1]])>=3)
        calleeLib <- paste(calleeLibGAV[[1]][[1]], calleeLibGAV[[1]][[2]], sep=":")
      else
        calleeLib <- calleeLibGAV[[1]][[1]]
      callerLibGAV = strsplit(df[i,1], ":")
      if(length(callerLibGAV[[1]])>=3)
        callerLib <- paste(callerLibGAV[[1]][[1]], callerLibGAV[[1]][[2]], sep=":")
      else
        callerLib <- callerLibGAV[[1]][[1]]
    }
    fieldName <- paste(df[i,2], df[i,5])
    if(is.null(callee_fields[[calleeLib]])) {
      callee_fields[calleeLib] <- hash()
      callee_fields[[calleeLib]][[callerLib]] = c(fieldName)
    } else {
      if(is.null(callee_fields[[calleeLib]][[callerLib]])) {
        callee_fields[[calleeLib]][[callerLib]] = c(fieldName)
      } else {
        callee_fields[[calleeLib]][[callerLib]] = c(callee_fields[[calleeLib]][[callerLib]], fieldName)
      }
    }
    callee_fields[[calleeLib]][[callerLib]] = as.list(unique(callee_fields[[calleeLib]][[callerLib]]))
  }
}

callee_annotations <- hash() # library -> (client -> [list of methods])
file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-annotations.tsv", full.names = TRUE)
for (i in seq_along(file_list)) {
  filename = file_list[[i]]
  df <- read.csv(filename, sep='\t')
  for(i in 1:nrow(df)) 
  {
    if (is.character(df[i, 4]) && is.character(df[i, 7])) {
      calleeLibGAV = strsplit(df[i,7], ":")
      if(length(calleeLibGAV[[1]])>=3)
        calleeLib <- paste(calleeLibGAV[[1]][[1]], calleeLibGAV[[1]][[2]], sep=":")
      else
        calleeLib <- calleeLibGAV[[1]][[1]]
      callerLibGAV = strsplit(df[i,4], ":")
      if(length(callerLibGAV[[1]])>=3)
        callerLib <- paste(callerLibGAV[[1]][[1]], callerLibGAV[[1]][[2]], sep=":")
      else
        callerLib <- callerLibGAV[[1]][[1]]
    }
    if(is.null(callee_annotations[[calleeLib]])) {
      callee_annotations[calleeLib] <- hash()
      callee_annotations[[calleeLib]][[callerLib]] = c(df[i,5])
    } else {
      if(is.null(callee_annotations[[calleeLib]][[callerLib]])) {
        callee_annotations[[calleeLib]][[callerLib]] = c(df[i,5])
      } else {
        callee_annotations[[calleeLib]][[callerLib]] = c(callee_annotations[[calleeLib]][[callerLib]], df[i,5])
      }
    }
    callee_annotations[[calleeLib]][[callerLib]] = as.list(unique(callee_annotations[[calleeLib]][[callerLib]]))
  }
}

callee_types <- hash() # library -> (client -> [list of methods])
file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-subtyping.tsv", full.names = TRUE)
for (i in seq_along(file_list)) {
  filename = file_list[[i]]
  df <- read.csv(filename, sep='\t')
  for(i in 1:nrow(df)) 
  {
    if (is.character(df[i, 2]) && is.character(df[i, 5])) {
      calleeLibGAV = strsplit(df[i,5], ":")
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
    if(is.null(callee_types[[calleeLib]])) {
      callee_types[calleeLib] <- hash()
      callee_types[[calleeLib]][[callerLib]] = c(df[i,3])
    } else {
      if(is.null(callee_types[[calleeLib]][[callerLib]])) {
        callee_types[[calleeLib]][[callerLib]] = c(df[i,3])
      } else {
        callee_types[[calleeLib]][[callerLib]] = c(callee_types[[calleeLib]][[callerLib]], df[i,3])
      }
    }
    callee_types[[calleeLib]][[callerLib]] = as.list(unique(callee_types[[calleeLib]][[callerLib]]))
  }
}

getValsfromCallee <- function(callee) {
  vals <- c()
  
  if (is.null(callee)) return(vals)
  for (client in keys(callee)) {
    vals <- c(vals, callee[[client]])
  }
  return(vals)
}
getValsfromCallee(callee_fields)

finalDf <- data.frame("Library"=character(),
                      "No. of Clients Using Methods"=integer(), "No. of Methods in Lib"=integer(), "No. of Distinct Methods Used By All Clients"=integer(), "Total No. of Methods Used By All Clients"=integer(),
                      "No. of Clients Using Fields"=integer(), "No. of Fields in Lib"=integer(), "No. of Distinct Fields Used By All Clients"=integer(), "Total No. of Fields Used By All Clients"=integer(), 
                      "No. of Clients Using Types"=integer(), "No. of Types in Lib"=integer(), "No. of Distinct Types Used By All Clients"=integer(), "Total No. of Types Used By All Clients"=integer())
for (lib in libraries) {
  row = data.frame(list("Library"=lib, "No. of Clients Using Methods"=length(callee_methods[[lib]]), "No. of Methods in Lib"=totalMethods[[lib]], "No. of Distinct Methods Used By All Clients"=length(unique(getValsfromCallee(callee_methods[[lib]]))), "Total No. of Methods Used By All Clients"=length(getValsfromCallee(callee_methods[[lib]])),
                        "No. of Clients Using Fields"=length(callee_fields[[lib]]), "No. of Fields in Lib"= totalFields[[lib]], "No. of Distinct Fields Used By All Clients"=length(unique(getValsfromCallee(callee_fields[[lib]]))), "Total No. of Fields Used By All Clients"=length(getValsfromCallee(callee_fields[[lib]])),
                        "No. of Clients Using Types"=length(callee_types[[lib]]), "No. of Types in Lib"=totalClasses[[lib]],"No. of Distinct Types Used By All Clients"=length(unique(getValsfromCallee(callee_types[[lib]]))), "Total No. of Types Used By All Clients"=length(getValsfromCallee(callee_types[[lib]]))
                        ))
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

tab_df(
  finalDf,
  title = "Standard API Usage",
  footnote = NULL,
  col.header = c("Library", "No. of Clients Using Methods", "No. of Methods in Lib", "No. of Distinct Methods Used By All Clients", "Total No. of Methods Used By All Clients",
                 "No. of Clients Using Fields", "No. of Fields in Lib", "No. of Distinct Fields Used By All Clients", "Total No. of Fields Used By All Clients", 
                 "No. of Clients Using Types", "No. of Types in Lib", "No. of Distinct Types Used By All Clients", "Total No. of Types Used By All Clients"),
  sort.column = 1,
  CSS = list(css.centeralign = 'text-align: left;')
)
colnames(finalDf) = c("Library", "No. of Clients Using Methods", "No. of Methods in Lib", "No. of Distinct Methods Used By All Clients", "Total No. of Methods Used By All Clients",
                      "No. of Clients Using Fields", "No. of Fields in Lib", "No. of Distinct Fields Used By All Clients", "Total No. of Fields Used By All Clients", 
                      "No. of Clients Using Types", "No. of Types in Lib", "No. of Distinct Types Used By All Clients", "Total No. of Types Used By All Clients")

print(xtable(finalDf, 
             caption = "Standard API Usage", digits = 0, colnames(c("Library", "No. of Clients Using Methods", "No. of Methods in Lib", "No. of Distinct Methods Used By All Clients", "Total No. of Methods Used By All Clients",
                                                                    "No. of Clients Using Fields", "No. of Fields in Lib", "No. of Distinct Fields Used By All Clients", "Total No. of Fields Used By All Clients", 
                                                                    "No. of Clients Using Types", "No. of Types in Lib", "No. of Distinct Types Used By All Clients", "Total No. of Types Used By All Clients"))), 
      file = "Documents/Waterloo/PL/21.icse.library-usage/tables/results/standard-api-usage.tex",size="small", include.rownames = FALSE)

