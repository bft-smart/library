package bftsmart.correctable;

public enum Consistency {
    NONE, // does not offer consistency ( 1 vote )
    WEAK, // offers weak consistency under t ( t*Vmax+1 votes )
    LINE, // offers linearizability under t ( 2t*Vmax+1 votes ) 
    FINAL // offers linearizability under T ( 2t*Vmax+1 votes AND responces > (N+2T-(t+1))/2 )
}
