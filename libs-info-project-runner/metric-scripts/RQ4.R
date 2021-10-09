# Jaccard Similarity = intersection/union
libraries <- c("com.alibaba:fastjson", "org.apache.commons:commons-collections4", "commons-io:commons-io", "joda-time:joda-time", 
               "", "", "org.jsoup:jsoup", "", "com.fasterxml.jackson.core:jackson-databind", "com.fasterxml.jackson.core:jackson-core") #gson,org.json,slf4j
jackson_databind_clients <- c("io.rest-assured:rest-assured-common", "org.thymeleaf:thymeleaf", "com.capitalone.dashboard:core", 
                              "io.springside:springside-core", "io.geekidea:spring-boot-plus")
libsAndClients <- hash()
libsAndClients[["com.fasterxml.jackson.core:jackson-databind"]] <- jackson_databind_clients

callee_methods <- hash()

file_list = list.files(path="Documents/Waterloo/PL/calls-across-libs/libs-info-project-runner/api-surface-data", recursive = TRUE, pattern="*-invocations.tsv", full.names = TRUE)

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
    if(is.null(callee_methods[[callerLib]]))
      callee_methods[[callerLib]] = c(df[i,6])
    else 
      callee_methods[[callerLib]] = c(callee_methods[[callerLib]], df[i,6])
    callee_methods[[callerLib]] = unique(callee_methods[[callerLib]])

  }
}
print(keys(callee_methods))
print(callee_methods[["io.rest-assured:json-path"]])

x <- c(5,6,7,8)
y <- c(7,8,9,10,11)
print(setdiff(x,y))
print(setequal(x,y))
union(x, y)
intersect(x, y)
