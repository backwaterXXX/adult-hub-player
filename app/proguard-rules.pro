# Release builds run with R8 full mode. Keep rules here only for classes whose
# reflection-based usage R8 cannot model; library consumer rules (Retrofit, Media3)
# and R8's built-in kotlinx.serialization support cover the rest.
