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

file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-setAccessibleCalls.tsv", full.names = TRUE)
writeLines("CallerLibrary\tCallerMethod\tCalleeLibrary\tsetAccessible.CalledOn\tVisibility\tCalleeName\tFieldSignature\tCounts","Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/visual-data/RQ2-libtoclient-setAccessibleCalls.tsv")


for (i in seq_along(file_list)) {
  filename = file_list[[i]]
 
  if (endsWith(filename,"RQ2-setAccessibleCalls.tsv") | endsWith(filename, "RQ2-libtoclient-setAccessibleCalls.tsv"))
    next

    subdirs = str_split(filename,"/")
  client =subdirs[[1]][length(subdirs[[1]])-1]
  
  df <- read.csv(filename, sep='\t')
  filteredDf = filter(df, Caller.Library != Callee.Library, !grepl("unknownLib", Caller.Library, fixed=TRUE), !grepl("unknownLib", Callee.Library, fixed=TRUE))
  filteredDf$Caller.Library <- getVersionlessLibs(filteredDf$Caller.Library)
  filteredDf <- subset(filteredDf, filteredDf$Callee.Library==client)
  
  if (nrow(filteredDf)==0) next
  counts = filteredDf %>% group_by(Caller.Library, Caller.Method, Callee.Library, setAccessible.Called.On, Visibility, Callee.Name, Field.Signature) %>% summarise(Count = n())
  write.table(counts,"Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/visual-data/RQ2-libtoclient-setAccessibleCalls.tsv",sep="\t",row.names=FALSE, col.names=FALSE, append=TRUE)
}

df <- read.csv("Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data/visual-data/RQ2-libtoclient-setAccessibleCalls.tsv", sep='\t')

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
df$CalleeLibrary <- getVersionlessLibs(df$CalleeLibrary)
df = df[!duplicated(c(df$CalleeName, df$FieldSignature)),]

# setAccessible
aggDf <- aggregate(df$Count, by=list(df$CalleeLibrary, df$CallerLibrary, df$setAccessible.CalledOn, df$Visibility), FUN=length)
countClients <- data.frame(df$CallerLibrary, df$CalleeLibrary)
aggClients <- aggregate(countClients$df.CallerLibrary, by=list(countClients$df.CalleeLibrary), function(df.CalleeLibrary) length(unique(df.CalleeLibrary)))

totalsDf <- aggregate(df$Count, by=list(df$CalleeLibrary, df$CallerLibrary, df$setAccessible.CalledOn), FUN=length)
mergedDf <- merge(aggDf, totalsDf, by = c("Group.1","Group.2","Group.3"))

counts <- hash()
for( i in rownames(mergedDf) ) {
  if (grepl("commons-collections", mergedDf[i, "Group.1"], fixed = TRUE))
    mergedDf[i, "Group.1"] <- "org.apache.commons:commons-collections4"
  if (grepl("commons-collections", mergedDf[i, "Group.2"], fixed = TRUE))
    mergedDf[i, "Group.2"] <- "org.apache.commons:commons-collections4"
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

intmdDf <- data.frame("Library"=character(), "Client"=character(), "Object"=character(), "default"=integer(), "private"=integer(), "protected"=integer(), "public"=integer(), "total"=integer(), check.names = FALSE)
for (lib in keys(counts)) {
  if (is.null(counts[[lib]][["protected"]])) counts[[lib]][["protected"]] = 0
  if (is.null(counts[[lib]][["public"]])) counts[[lib]][["public"]] = 0
  if (is.null(counts[[lib]][["default"]])) counts[[lib]][["default"]] = 0
  if (is.null(counts[[lib]][["total"]])) counts[[lib]][["total"]] = 0
  row = data.frame(list("Library"=str_split(lib, pattern=" ")[[1]][1], "Client"=str_split(lib, pattern=" ")[[1]][2], "Object"=str_split(lib, pattern=" ")[[1]][3], "default"=counts[[lib]][["default"]], "private"=counts[[lib]][["private"]], 
                        "protected"=counts[[lib]][["protected"]], "public"=counts[[lib]][["public"]], "total"=counts[[lib]][["total"]]), check.names = FALSE)
  intmdDf = rbind(intmdDf, row)
}

countObjs <- data.frame(intmdDf$Library, intmdDf$Client, intmdDf$Object, intmdDf$default, intmdDf$private, intmdDf$protected, intmdDf$public, intmdDf$total)
aggObjs <- aggregate(cbind(countObjs$intmdDf.default, countObjs$intmdDf.private, countObjs$intmdDf.protected, countObjs$intmdDf.public, countObjs$intmdDf.total), by=list(countObjs$intmdDf.Library,countObjs$intmdDf.Client, countObjs$intmdDf.Object), sum)
totalsObjs <- aggregate(cbind(countObjs$intmdDf.default, countObjs$intmdDf.private, countObjs$intmdDf.protected, countObjs$intmdDf.public, countObjs$intmdDf.total), by=list(countObjs$intmdDf.Library, countObjs$intmdDf.Client), sum)
finalDf <- data.frame("Library"=character(), "Client"=integer(), "Fields"=integer(), "Constructors"=integer(), "Methods"=integer(), 
                      "default"=integer(), "private"=integer(), "protected"=integer(), "public"=integer(), "total"=integer(), check.names = FALSE)

for (i in 1:nrow(aggObjs)) {
  lib <- aggObjs[i,"Group.1"]
  client <- aggObjs[i,"Group.2"]

  obj <- "java.lang.reflect.Constructor"
  constr <- subset(aggObjs, (aggObjs$Group.1==lib & aggObjs$Group.2==client & aggObjs$Group.3==obj))
  noOfConstructors <- if (!identical(constr$V5 , numeric(0))) constr$V5  else 0
  obj <- "java.lang.reflect.Field"
  fields <- subset(aggObjs, (aggObjs$Group.1==lib & aggObjs$Group.2==client & aggObjs$Group.3==obj))
  noOfFields <- if (!identical(fields$V5 , numeric(0))) fields$V5  else 0
  obj <- "java.lang.reflect.Method"
  methods <- subset(aggObjs, (aggObjs$Group.1==lib & aggObjs$Group.2==client & aggObjs$Group.3==obj))
  noOfMethods <- if(!identical(methods$V5 , numeric(0))) methods$V5  else 0
  totals <- subset(totalsObjs, totalsObjs$Group.1==lib & totalsObjs$Group.2==client)
  row = data.frame(list("Library"=client, "Client"=lib, "Fields"=noOfFields, "Constructors"=noOfConstructors, "Methods"=noOfMethods,
                        "default"=totals$V1, "private"=totals$V2, "protected"=totals$V3, "public"=totals$V4, "total"=totals$V5), 
                   check.names = FALSE)
  finalDf = rbind(finalDf, row)
}

finalDf <- finalDf %>% distinct()
finalDf$Library <- getArtifactNames(finalDf$Library)
finalDf$Client <- getArtifactNames(finalDf$Client)
finalDf$Library[finalDf$Library == "core"] <- "capitalone.dashboard:core"
finalDf$Client[finalDf$Client == "core"] <- "capitalone.dashboard:core"

tab_df(
  finalDf,
  align = c('l','r','r','r','r','r','r','r','r','r', 'r'),
  title = "setAccessible Calls - Library to client",
  footnote = NULL,
  col.header = c("Library", "Visibility", "Count", "Total"),
  sort.column = 1,
  CSS = list(css.centeralign = 'text-align: right;')
)


print(xtable(finalDf, align = c('l','r','r','r','r','r','r','r','r','r', 'r'),
             caption = "setAccessible Calls - Library to client", digits = 0),
      file = "Documents/Waterloo/PL/21.icse.library-usage/tables/results/set-accessible-lib-to-client.tex",size="small",include.rownames = FALSE)

