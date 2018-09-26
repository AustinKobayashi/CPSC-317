In this assignment I cached almost every result. The outcome of this is that 
the output with verbose tracing on has slightly different results than some
of the sample traces. The end result is the same, but my client performs 
fewer queries since it either caches more records or starts its search at a
deeper level than the client used for the sample traces.

I’ll use an example to clarify:

In “Sample trace - cname” which is run using 
“java -jar DNSLookupService.jar 199.7.83.42” and performs
“lookup prep.ai.mit.edu” traces the output down to the result
“prep.ai.mit.edu                A     300      208.118.235.20”

A second search is performed using
“lookup prep.ai.mit.edu AAAA”
and from the sample trace, we can see that the search starts querying at
“Query ID     8649 ftp.gnu.org  AAAA --> 199.7.83.42”
This is good since we have cached that ftp.gnu.org is a cname for
prep.ai.mit.edu, but I made use of my cache more.

In the first query, we found the name servers for gnu.org, namely:
Nameservers (4)
       gnu.org                        300        NS   ns3.gnu.org
       gnu.org                        300        NS   ns4.gnu.org
       gnu.org                        300        NS   ns2.gnu.org
       gnu.org                        300        NS   ns1.gnu.org
Additional Information (6)
       ns1.gnu.org                    300        A    208.118.235.164
       ns1.gnu.org                    300        AAAA 2001:4830:134:3:0:0:0:f
       ns2.gnu.org                    300        A    87.98.253.102
       ns3.gnu.org                    300        A    46.43.37.70
       ns3.gnu.org                    300        AAAA 2001:41c8:20:2d3:0:0:0:a
       ns4.gnu.org                    300        A    208.70.31.125 

Since we know that ftp.gnu.org is a cname for prep.ai.mit.edu, and we know the
name servers for gnu.org, it would be wasteful to perform the second search
“lookup prep.ai.mit.edu AAAA” at the root server provided on the command line.
The client used in the sample trace optimizes the second search by using the 
cache so that the cname for prep.ai.mit.edu is not rediscovered. My client
takes this further and uses the cache to find that prep.ai.mit.edu has 
ftp.gnu.org as a cname. It will then use the cache to find that we already have
the name servers for gnu.org, so it will start subsequent searches for 
prep.ai.mit.edu by using ftp.gnu.org and the name server corresponding to 
gnu.org. So on my client, the second search
“lookup prep.ai.mit.edu” 
will start at
“Query ID     944 ftp.gnu.org  AAAA --> 208.118.235.164”. 
You can see here that it uses the cache to find the cname, and the name server 
for gnu.org.

In summary, for the “Sample trace - cname”, the sample client uses the cache
to find the cname for prep.ai.mit.edu so that subsequent searches do not need
to rediscover the cname. My client uses the cache to find the cname for 
prep.ai.mit.edu and it uses the cache to find name servers for the cname so that
the cname, and the name servers for the cname do not need to be rediscovered.
This means that my client may perform less searches than the sample client, 
which may result in a different trace output from the two clients.


My client performs this behaviour on more than just the sample output described
above. When performing a query, it will check if there are cached servers for
each label of the domain. So for ftp.gnu.org, it will check if there are cached
servers for ftp.gnu.org, then if none are found it will check for cached servers
for gnu.org, then org.





